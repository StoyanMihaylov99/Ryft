package com.application.ryft.seed;

import com.application.ryft.issues.dto.ChangeIssueStatusRequest;
import com.application.ryft.issues.dto.ComponentResponse;
import com.application.ryft.issues.dto.CreateCommentRequest;
import com.application.ryft.issues.dto.CreateComponentRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.CreateLabelRequest;
import com.application.ryft.issues.dto.CreateSubtaskRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.LabelResponse;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.service.CommentService;
import com.application.ryft.issues.service.ComponentService;
import com.application.ryft.issues.service.IssueService;
import com.application.ryft.issues.service.LabelService;
import com.application.ryft.projects.dto.AddProjectMemberRequest;
import com.application.ryft.projects.dto.CreateProjectRequest;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.service.ProjectService;
import com.application.ryft.search.dto.CreateSavedFilterRequest;
import com.application.ryft.search.dto.IssueSearchRequest;
import com.application.ryft.search.service.SavedFilterService;
import com.application.ryft.seed.data.SeedComment;
import com.application.ryft.seed.data.SeedComponent;
import com.application.ryft.seed.data.SeedIssue;
import com.application.ryft.seed.data.SeedLabel;
import com.application.ryft.seed.data.SeedMembership;
import com.application.ryft.seed.data.SeedProject;
import com.application.ryft.seed.data.SeedSavedFilter;
import com.application.ryft.seed.data.SeedSprint;
import com.application.ryft.seed.data.SeedSprintOutcome;
import com.application.ryft.sprints.dto.CreateSprintRequest;
import com.application.ryft.sprints.dto.SprintResponse;
import com.application.ryft.sprints.service.BacklogService;
import com.application.ryft.sprints.service.SprintService;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import com.application.ryft.workflow.service.WorkflowService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds one {@link SeedProject} entirely through the public service interfaces of {@code projects},
 * {@code issues}, {@code workflow}, {@code sprints} and {@code search} — never their repositories or
 * entities, same module-boundary rule every other cross-module caller in this codebase follows (see
 * ARCHITECTURE.md's "module boundary rule"). Every write is made as the project's Owner (the demo user)
 * except where a {@link SeedIssue} names a different reporter/comment author, mirroring who would
 * actually perform that action in a real team. Deliberately an ordinary, always-registered
 * {@code @Component}, not gated by {@code @ConditionalOnProperty} — see {@link DemoDataInstaller}'s
 * javadoc for why (this class does nothing unless {@link #install} is called, and the only production
 * caller is the gated {@code DemoDataSeeder}).
 *
 * <p><b>Known, documented history limitation:</b> {@code SprintService#complete} always stamps
 * {@code completedAt} as {@code Instant.now()}, and {@code IssueService#changeStatus} always stamps a
 * newly-DONE issue's {@code resolvedAt} the same way — neither has a public API to backdate either
 * value, and reaching into their entities to force one would defeat the point of going through the
 * service layer at all. So a "completed" sprint's {@code startDate}/{@code endDate} are genuinely
 * historical (set via {@link CreateSprintRequest}, which does accept arbitrary dates), but every issue
 * resolved while seeding it — and the sprint's own {@code completedAt} — are stamped at seed time, not
 * spread across that historical window. Concretely: only the *most recently completed* sprint in each
 * project's plan has its {@code endDate} pinned to "today" (see {@code SeedSprint}'s javadoc), which puts
 * the seed-time resolutions inside its plotted burndown range and gives its actual-burndown line a real,
 * visible drop rather than a flat one. Any *older* completed sprint's window ends before seed time, so its
 * actual-burndown line is flat at {@code committedPoints} for its whole plotted range — not an
 * approximation of "real" history, just a plain, accepted limitation: it shows no visible progress,
 * because {@code BurndownServiceImpl} has no per-day activity log to reconstruct one from (see its own
 * javadoc's "v1 limitation" note). Velocity is unaffected either way, since
 * {@code VelocityServiceImpl} sums story points and orders by {@code completedAt}, neither of which
 * depends on where a sprint's dates fall relative to "today".
 */
@Component
class DemoProjectInstaller {

    private final ProjectService projectService;
    private final IssueService issueService;
    private final CommentService commentService;
    private final LabelService labelService;
    private final ComponentService componentService;
    private final WorkflowService workflowService;
    private final SprintService sprintService;
    private final BacklogService backlogService;
    private final SavedFilterService savedFilterService;

    DemoProjectInstaller(ProjectService projectService, IssueService issueService, CommentService commentService,
            LabelService labelService, ComponentService componentService, WorkflowService workflowService,
            SprintService sprintService, BacklogService backlogService, SavedFilterService savedFilterService) {
        this.projectService = projectService;
        this.issueService = issueService;
        this.commentService = commentService;
        this.labelService = labelService;
        this.componentService = componentService;
        this.workflowService = workflowService;
        this.sprintService = sprintService;
        this.backlogService = backlogService;
        this.savedFilterService = savedFilterService;
    }

    void install(SeedProject plan, UUID ownerId, Map<String, UUID> userIdsByEmail) {
        ProjectResponse project = projectService.create(ownerId,
                new CreateProjectRequest(plan.key(), plan.name(), plan.description()));
        addMembers(project.key(), ownerId, plan.members());

        Map<String, UUID> labelIds = createLabels(project.key(), ownerId, plan.labels());
        Map<String, UUID> componentIds = createComponents(project.key(), ownerId, plan.components());
        Map<StatusCategory, UUID> statusIds = resolveStatusIds(ownerId, project.key());
        Map<String, UUID> sprintIds = createSprints(project.key(), ownerId, plan.sprints());

        Map<String, IssueResponse> issuesByTitle = new LinkedHashMap<>();
        List<IssueResponse> backlogIssuesInOrder = createIssues(project.key(), plan.issues(), ownerId,
                userIdsByEmail, labelIds, componentIds, statusIds, sprintIds, issuesByTitle);

        demonstrateBacklogRanking(ownerId, backlogIssuesInOrder);
        advanceSprints(ownerId, plan.sprints(), sprintIds);
        createSavedFilters(project.key(), ownerId, plan.savedFilters());
    }

    private void addMembers(String projectKey, UUID ownerId, List<SeedMembership> members) {
        for (SeedMembership membership : members) {
            projectService.addMember(ownerId, projectKey,
                    new AddProjectMemberRequest(membership.userEmail(), membership.role()));
        }
    }

    private Map<String, UUID> createLabels(String projectKey, UUID ownerId, List<SeedLabel> labels) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (SeedLabel label : labels) {
            LabelResponse created = labelService.create(ownerId, projectKey,
                    new CreateLabelRequest(label.name(), label.color()));
            ids.put(label.name(), created.id());
        }
        return ids;
    }

    private Map<String, UUID> createComponents(String projectKey, UUID ownerId, List<SeedComponent> components) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (SeedComponent component : components) {
            ComponentResponse created = componentService.create(ownerId, projectKey,
                    new CreateComponentRequest(component.name()));
            ids.put(component.name(), created.id());
        }
        return ids;
    }

    /** The lowest-{@code sortOrder} status per category — a representative status to move a seed issue into. */
    private Map<StatusCategory, UUID> resolveStatusIds(UUID ownerId, String projectKey) {
        WorkflowSchemeResponse scheme = workflowService.getSchemeForProject(ownerId, projectKey);
        Map<StatusCategory, UUID> ids = new EnumMap<>(StatusCategory.class);
        for (WorkflowStatusResponse status : scheme.statuses()) {
            ids.putIfAbsent(status.category(), status.id());
        }
        return ids;
    }

    private Map<String, UUID> createSprints(String projectKey, UUID ownerId, List<SeedSprint> sprints) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        // UTC, not the host default zone: BurndownServiceImpl computes "today" via
        // LocalDate.now(ZoneOffset.UTC) (see its javadoc, and ARCHITECTURE.md's matching-clocks rule) —
        // an offset computed against the wrong "today" could land a sprint's endDate a day off from what
        // SeedSprint's offsets actually intend, right at the boundary this seeder deliberately controls
        // (see SeedSprint's javadoc on pinning the most recent completed sprint's endDate to "today").
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (SeedSprint sprint : sprints) {
            LocalDate startDate = today.plusDays(sprint.startOffsetDays());
            LocalDate endDate = today.plusDays(sprint.endOffsetDays());
            SprintResponse created = sprintService.create(ownerId, projectKey,
                    new CreateSprintRequest(sprint.name(), sprint.goal(), startDate, endDate));
            ids.put(sprint.name(), created.id());
        }
        return ids;
    }

    /**
     * Creates every issue in {@code plan}'s declared order, wiring up its epic/subtask parent, sprint
     * assignment, target status and comments as it goes. Returns the backlog (no-sprint), non-subtask
     * issues in creation order, for {@link #demonstrateBacklogRanking}.
     */
    private List<IssueResponse> createIssues(String projectKey, List<SeedIssue> plannedIssues, UUID ownerId,
            Map<String, UUID> userIdsByEmail, Map<String, UUID> labelIds, Map<String, UUID> componentIds,
            Map<StatusCategory, UUID> statusIds, Map<String, UUID> sprintIds, Map<String, IssueResponse> issuesByTitle) {
        List<IssueResponse> backlogIssuesInOrder = new ArrayList<>();
        for (SeedIssue seedIssue : plannedIssues) {
            UUID creatorId = resolveUser(seedIssue.reporterEmail(), ownerId, userIdsByEmail);
            IssueResponse created = createIssue(projectKey, seedIssue, creatorId, userIdsByEmail, labelIds,
                    componentIds, issuesByTitle);

            if (seedIssue.sprintName() != null) {
                UUID sprintId = requireNonNull(sprintIds, seedIssue.sprintName(), "sprint");
                created = backlogService.moveIssue(ownerId, created.key(), sprintId);
            } else if (seedIssue.type() != IssueType.SUBTASK && seedIssue.type() != IssueType.EPIC) {
                backlogIssuesInOrder.add(created);
            }

            if (seedIssue.targetStatusCategory() != null) {
                UUID statusId = requireNonNull(statusIds, seedIssue.targetStatusCategory(), "workflow status");
                created = issueService.changeStatus(creatorId, created.key(), new ChangeIssueStatusRequest(statusId));
            }

            issuesByTitle.put(seedIssue.title(), created);
            addComments(seedIssue.comments(), created.key(), userIdsByEmail);
        }
        return backlogIssuesInOrder;
    }

    private IssueResponse createIssue(String projectKey, SeedIssue seedIssue, UUID creatorId,
            Map<String, UUID> userIdsByEmail, Map<String, UUID> labelIds, Map<String, UUID> componentIds,
            Map<String, IssueResponse> issuesByTitle) {
        UUID assigneeId = seedIssue.assigneeEmail() == null ? null : userIdsByEmail.get(seedIssue.assigneeEmail());

        if (seedIssue.type() == IssueType.SUBTASK) {
            IssueResponse parent = requireIssue(issuesByTitle, seedIssue.parentTitle());
            return issueService.createSubtask(creatorId, parent.key(),
                    new CreateSubtaskRequest(seedIssue.title(), seedIssue.description(), seedIssue.priority(),
                            assigneeId));
        }

        UUID parentId = seedIssue.parentTitle() == null ? null
                : requireIssue(issuesByTitle, seedIssue.parentTitle()).id();
        List<UUID> labels = seedIssue.labelNames().stream()
                .map(name -> requireNonNull(labelIds, name, "label"))
                .toList();
        List<UUID> components = seedIssue.componentNames().stream()
                .map(name -> requireNonNull(componentIds, name, "component"))
                .toList();
        return issueService.create(creatorId, projectKey,
                new CreateIssueRequest(seedIssue.type(), seedIssue.title(), seedIssue.description(),
                        seedIssue.priority(), assigneeId, seedIssue.storyPoints(), parentId, labels, components));
    }

    private void addComments(List<SeedComment> comments, String issueKey, Map<String, UUID> userIdsByEmail) {
        for (SeedComment comment : comments) {
            UUID authorId = requireNonNull(userIdsByEmail, comment.authorEmail(), "comment author");
            commentService.create(authorId, issueKey, new CreateCommentRequest(comment.body()));
        }
    }

    /**
     * Moves the most recently created backlog issue to the very top of the backlog, ahead of the first
     * one created — a small, concrete demonstration that {@code IssueService#reorderBacklog}'s manual
     * ranking actually works, on top of the ranking every issue already gets implicitly at creation time.
     */
    private void demonstrateBacklogRanking(UUID ownerId, List<IssueResponse> backlogIssuesInOrder) {
        if (backlogIssuesInOrder.size() < 2) {
            return;
        }
        IssueResponse mostRecent = backlogIssuesInOrder.get(backlogIssuesInOrder.size() - 1);
        IssueResponse first = backlogIssuesInOrder.get(0);
        issueService.reorderBacklog(ownerId, mostRecent.key(), null, first.key());
    }

    /** Starts every sprint, then completes the ones planned as {@link SeedSprintOutcome#COMPLETED}. */
    private void advanceSprints(UUID ownerId, List<SeedSprint> sprints, Map<String, UUID> sprintIds) {
        for (SeedSprint sprint : sprints) {
            UUID sprintId = requireNonNull(sprintIds, sprint.name(), "sprint");
            sprintService.start(ownerId, sprintId);
            if (sprint.outcome() == SeedSprintOutcome.COMPLETED) {
                sprintService.complete(ownerId, sprintId);
            }
        }
    }

    private void createSavedFilters(String projectKey, UUID ownerId, List<SeedSavedFilter> savedFilters) {
        for (SeedSavedFilter filter : savedFilters) {
            List<UUID> assigneeIds = filter.isOnlyMine() ? List.of(ownerId) : null;
            IssueSearchRequest query = new IssueSearchRequest(assigneeIds, null, null, null, filter.types(), null,
                    null);
            savedFilterService.create(ownerId, projectKey,
                    new CreateSavedFilterRequest(filter.name(), query, filter.isShared()));
        }
    }

    private UUID resolveUser(String email, UUID defaultUserId, Map<String, UUID> userIdsByEmail) {
        return email == null ? defaultUserId : requireNonNull(userIdsByEmail, email, "user");
    }

    private IssueResponse requireIssue(Map<String, IssueResponse> issuesByTitle, String title) {
        return requireNonNull(issuesByTitle, title, "seed issue titled");
    }

    private <K, V> V requireNonNull(Map<K, V> map, K key, String what) {
        V value = map.get(key);
        if (value == null) {
            throw new IllegalStateException("Demo data references unknown " + what + ": " + key);
        }
        return value;
    }
}
