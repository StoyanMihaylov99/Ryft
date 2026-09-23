package com.application.ryft.issues.service;

import com.application.ryft.issues.dto.ChangeIssueStatusRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.CreateSubtaskRequest;
import com.application.ryft.issues.dto.EpicProgressResponse;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.IssueSearchCriteria;
import com.application.ryft.issues.dto.UpdateIssueRequest;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssueKeySequence;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.exception.AssigneeNotAProjectMemberException;
import com.application.ryft.issues.exception.IllegalStatusTransitionException;
import com.application.ryft.issues.exception.InsufficientProjectRoleException;
import com.application.ryft.issues.exception.InvalidParentLinkException;
import com.application.ryft.issues.exception.IssueNotFoundException;
import com.application.ryft.issues.exception.NotAnEpicException;
import com.application.ryft.issues.repository.CommentRepository;
import com.application.ryft.issues.repository.IssueKeySequenceRepository;
import com.application.ryft.issues.repository.IssueRepository;
import com.application.ryft.common.event.IssueAssigneeChangedEvent;
import com.application.ryft.common.event.IssueCreatedEvent;
import com.application.ryft.common.event.IssueStatusChangedEvent;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueServiceImpl implements IssueService {

    private static final double RANK_STEP = 1000.0;

    private final IssueRepository issueRepository;
    private final IssueKeySequenceRepository issueKeySequenceRepository;
    private final IssueProjectAccess projectAccess;
    private final CommentRepository commentRepository;
    private final IssueLabelingService issueLabelingService;
    private final IssueWorkflowAccess issueWorkflowAccess;
    private final ApplicationEventPublisher eventPublisher;

    public IssueServiceImpl(IssueRepository issueRepository, IssueKeySequenceRepository issueKeySequenceRepository,
            IssueProjectAccess projectAccess, CommentRepository commentRepository,
            IssueLabelingService issueLabelingService, IssueWorkflowAccess issueWorkflowAccess,
            ApplicationEventPublisher eventPublisher) {
        this.issueRepository = issueRepository;
        this.issueKeySequenceRepository = issueKeySequenceRepository;
        this.projectAccess = projectAccess;
        this.commentRepository = commentRepository;
        this.issueLabelingService = issueLabelingService;
        this.issueWorkflowAccess = issueWorkflowAccess;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public IssueResponse create(UUID callerId, String projectKey, CreateIssueRequest request) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        requireNotViewer(role);
        if (request.assigneeId() != null) {
            requireAssigneeIsProjectMember(callerId, projectKey, request.assigneeId());
        }
        validateParentOnCreate(request.type(), request.parentId(), project.id());
        issueLabelingService.validateOnCreate(project.id(), request.labelIds(), request.componentIds());

        IssuePriority priority = request.priority() != null ? request.priority() : IssuePriority.MEDIUM;
        String description = request.description() == null ? null : request.description().trim();
        String issueKey = nextIssueKey(project.id(), project.key());
        double rank = nextBacklogRank(project.id());
        WorkflowSchemeResponse scheme = issueWorkflowAccess.requireScheme(callerId, projectKey);

        Issue issue = new Issue(project.id(), issueKey, request.type(), request.title().trim(), description,
                priority, request.assigneeId(), callerId, rank);
        issue.setWorkflowStatusId(initialStatusId(scheme));
        issue.setStoryPoints(request.storyPoints());
        issue.setParentIssueId(request.parentId());
        Issue saved = issueRepository.save(issue);
        issueLabelingService.attachOnCreate(saved, project.id(), request.labelIds(), request.componentIds());
        eventPublisher.publishEvent(new IssueCreatedEvent(saved.getId(), saved.getKey(), project.id(), project.key(),
                callerId, saved.getAssigneeId(), saved.getReporterId(), saved.getTitle(), saved.getType().name()));
        return issueLabelingService.toResponse(saved, scheme, callerId, role);
    }

    /** The lowest-{@code sortOrder} status in the scheme — e.g. "To Do" in the fixed default. */
    private UUID initialStatusId(WorkflowSchemeResponse scheme) {
        return scheme.statuses().get(0).id();
    }

    @Override
    @Transactional
    public List<IssueResponse> listForProject(UUID callerId, String projectKey) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        return issueLabelingService.toResponses(
                issueRepository.findAllByProjectIdAndTypeNotOrderByCreatedAtAsc(project.id(), IssueType.SUBTASK),
                callerId, projectKey, role);
    }

    @Override
    @Transactional
    public List<IssueResponse> listForProject(UUID callerId, String projectKey, UUID sprintId) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        return issueLabelingService.toResponses(issueRepository
                .findAllByProjectIdAndSprintIdAndTypeNotOrderByCreatedAtAsc(project.id(), sprintId, IssueType.SUBTASK),
                callerId, projectKey, role);
    }

    @Override
    @Transactional
    public List<IssueResponse> listBacklogForProject(UUID callerId, String projectKey) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        return issueLabelingService.toResponses(issueRepository
                .findAllByProjectIdAndSprintIdIsNullAndTypeNotOrderByBacklogRankAsc(project.id(), IssueType.SUBTASK),
                callerId, projectKey, role);
    }

    @Override
    @Transactional
    public List<IssueResponse> listForProjectByEpic(UUID callerId, String projectKey, UUID epicId) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        return issueLabelingService.toResponses(
                issueRepository.findAllByProjectIdAndParentIssueIdOrderByCreatedAtAsc(project.id(), epicId),
                callerId, projectKey, role);
    }

    @Override
    @Transactional
    public List<IssueResponse> listForProjectByLabel(UUID callerId, String projectKey, UUID labelId) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        return issueLabelingService.toResponses(issueRepository
                .findAllByProjectIdAndLabelIdAndTypeNotOrderByCreatedAtAsc(project.id(), labelId, IssueType.SUBTASK),
                callerId, projectKey, role);
    }

    @Override
    @Transactional
    public List<IssueResponse> listForProjectByComponent(UUID callerId, String projectKey, UUID componentId) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        return issueLabelingService.toResponses(issueRepository
                .findAllByProjectIdAndComponentIdAndTypeNotOrderByCreatedAtAsc(project.id(), componentId,
                        IssueType.SUBTASK), callerId, projectKey, role);
    }

    @Override
    @Transactional
    public List<IssueResponse> listForSprint(UUID callerId, String projectKey, UUID sprintId) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        return issueLabelingService.toResponses(issueRepository
                .findAllByProjectIdAndSprintIdAndTypeNotOrderByCreatedAtAsc(project.id(), sprintId, IssueType.SUBTASK),
                callerId, projectKey, role);
    }

    /**
     * Not read-only for the same reason every other list method here isn't: transitively calls
     * {@link IssueLabelingService#toResponses}, which may backfill {@code workflowStatusId} on a legacy
     * row — see ARCHITECTURE.md's "readOnly + transitive lazy write" note.
     */
    @Override
    @Transactional
    public List<IssueResponse> search(UUID callerId, String projectKey, IssueSearchCriteria criteria) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        List<Issue> issues = issueRepository.search(project.id(), criteria, Sort.by("createdAt").ascending());
        return issueLabelingService.toResponses(issues, callerId, projectKey, role);
    }

    @Override
    @Transactional
    public IssueResponse get(UUID callerId, String issueKey) {
        Issue issue = requireIssue(issueKey);
        String projectKey = projectKeyOf(issue);
        projectAccess.requireMembership(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        return issueLabelingService.toResponse(issue, callerId, projectKey, role);
    }

    @Override
    @Transactional
    public IssueResponse update(UUID callerId, String issueKey, UpdateIssueRequest request) {
        Issue issue = requireIssue(issueKey);
        String projectKey = projectKeyOf(issue);
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        requireCanEditIssue(role, callerId, issue);

        UUID previousAssigneeId = issue.getAssigneeId();
        applyUpdate(issue, callerId, projectKey, request);
        publishAssigneeChangedIfNeeded(issue, project, callerId, previousAssigneeId);
        return issueLabelingService.toResponse(issue, callerId, projectKey, role);
    }

    private void publishAssigneeChangedIfNeeded(Issue issue, ProjectResponse project, UUID callerId,
            UUID previousAssigneeId) {
        UUID newAssigneeId = issue.getAssigneeId();
        if (newAssigneeId != null && !newAssigneeId.equals(previousAssigneeId)) {
            eventPublisher.publishEvent(new IssueAssigneeChangedEvent(issue.getId(), issue.getKey(), project.id(),
                    project.key(), callerId, previousAssigneeId, newAssigneeId));
        }
    }

    @Override
    @Transactional
    public IssueResponse changeStatus(UUID callerId, String issueKey, ChangeIssueStatusRequest request) {
        Issue issue = requireIssue(issueKey);
        String projectKey = projectKeyOf(issue);
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        requireNotViewer(role);

        WorkflowSchemeResponse scheme = issueWorkflowAccess.requireScheme(callerId, projectKey);
        issueLabelingService.backfillWorkflowStatusIfMissing(issue, scheme);
        UUID fromStatusId = issue.getWorkflowStatusId();
        UUID toStatusId = request.statusId();
        if (!issueWorkflowAccess.isTransitionLegal(callerId, projectKey, fromStatusId, toStatusId)) {
            throw new IllegalStatusTransitionException(fromStatusId, toStatusId);
        }
        setStatus(issue, toStatusId, scheme);
        eventPublisher.publishEvent(new IssueStatusChangedEvent(issue.getId(), issue.getKey(), project.id(),
                project.key(), callerId, issue.getAssigneeId(), issue.getReporterId(),
                statusNameOf(scheme, fromStatusId), statusNameOf(scheme, toStatusId)));
        return issueLabelingService.toResponse(issue, scheme, callerId, role);
    }

    private String statusNameOf(WorkflowSchemeResponse scheme, UUID statusId) {
        return scheme.statuses().stream()
                .filter(status -> status.id().equals(statusId))
                .map(WorkflowStatusResponse::name)
                .findFirst()
                .orElse(null);
    }

    private void applyUpdate(Issue issue, UUID callerId, String projectKey, UpdateIssueRequest request) {
        applyTitle(issue, request);
        applyDescription(issue, request);
        applyPriority(issue, request);
        applyAssignee(issue, callerId, projectKey, request);
        applyStoryPoints(issue, request);
        applyParentId(issue, request);
        issueLabelingService.applyLabels(issue, issue.getProjectId(), request.labelIds());
        issueLabelingService.applyComponents(issue, issue.getProjectId(), request.componentIds());
    }

    private void applyTitle(Issue issue, UpdateIssueRequest request) {
        if (request.title() != null && !request.title().isBlank()) {
            issue.setTitle(request.title().trim());
        }
    }

    private void applyDescription(Issue issue, UpdateIssueRequest request) {
        if (request.description() != null) {
            issue.setDescription(request.description().trim());
        }
    }

    private void applyPriority(Issue issue, UpdateIssueRequest request) {
        if (request.priority() != null) {
            issue.setPriority(request.priority());
        }
    }

    private void applyAssignee(Issue issue, UUID callerId, String projectKey, UpdateIssueRequest request) {
        if (request.assigneeId() != null) {
            requireAssigneeIsProjectMember(callerId, projectKey, request.assigneeId());
            issue.setAssigneeId(request.assigneeId());
        }
    }

    private void applyStoryPoints(Issue issue, UpdateIssueRequest request) {
        if (request.storyPoints() != null) {
            issue.setStoryPoints(request.storyPoints());
        }
    }

    private void applyParentId(Issue issue, UpdateIssueRequest request) {
        if (request.parentId() == null) {
            return;
        }
        if (request.parentId().equals(issue.getId())) {
            throw new InvalidParentLinkException("An issue cannot be linked to itself as its parent");
        }
        requireParentAllowedForType(issue.getType(), request.parentId(), issue.getProjectId());
        issue.setParentIssueId(request.parentId());
    }

    /**
     * {@code parentId == null} is valid for EPIC (no parent) and STORY/TASK/BUG (parent is optional);
     * it's rejected for SUBTASK by the create-only caller, {@link #validateParentOnCreate} — a null
     * check here would be redundant since {@code applyParentId} above never calls this with a null id
     * (null there means "leave the existing link alone").
     */
    private void requireParentAllowedForType(IssueType type, UUID parentId, UUID projectId) {
        switch (type) {
            case EPIC -> throw new InvalidParentLinkException("An Epic cannot have a parent");
            case SUBTASK -> requireStoryTaskOrBugParent(parentId, projectId);
            case STORY, TASK, BUG -> requireEpicParent(parentId, projectId);
        }
    }

    private void validateParentOnCreate(IssueType type, UUID parentId, UUID projectId) {
        if (parentId == null) {
            if (type == IssueType.SUBTASK) {
                throw new InvalidParentLinkException("A Subtask must have a parent issue");
            }
            return;
        }
        requireParentAllowedForType(type, parentId, projectId);
    }

    private Issue requireParentInProject(UUID parentId, UUID projectId) {
        Optional<Issue> parent = issueRepository.findByIdAndProjectId(parentId, projectId);
        if (parent.isEmpty()) {
            if (issueRepository.existsById(parentId)) {
                throw new InvalidParentLinkException("Issue " + parentId + " does not belong to this project");
            }
            throw new InvalidParentLinkException("Issue " + parentId + " does not exist");
        }
        return parent.get();
    }

    private void requireEpicParent(UUID parentId, UUID projectId) {
        Issue parent = requireParentInProject(parentId, projectId);
        if (parent.getType() != IssueType.EPIC) {
            throw new InvalidParentLinkException("Issue " + parent.getKey() + " is not an Epic");
        }
    }

    private void requireStoryTaskOrBugParent(UUID parentId, UUID projectId) {
        requireSubtaskableParentType(requireParentInProject(parentId, projectId));
    }

    private void requireSubtaskableParentType(Issue parent) {
        if (parent.getType() != IssueType.STORY && parent.getType() != IssueType.TASK
                && parent.getType() != IssueType.BUG) {
            throw new InvalidParentLinkException(
                    "Issue " + parent.getKey() + " cannot have Subtasks — only Story, Task, or Bug issues can");
        }
    }

    private void setStatus(Issue issue, UUID statusId, WorkflowSchemeResponse scheme) {
        issue.setWorkflowStatusId(statusId);
        boolean isDone = scheme.statuses().stream()
                .filter(status -> status.id().equals(statusId))
                .map(WorkflowStatusResponse::category)
                .anyMatch(category -> category == StatusCategory.DONE);
        issue.setResolvedAt(isDone ? Instant.now() : null);
    }

    @Override
    @Transactional
    public void delete(UUID callerId, String issueKey) {
        Issue issue = requireIssue(issueKey);
        String projectKey = projectKeyOf(issue);
        projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);

        if (issue.getType() == IssueType.EPIC) {
            unlinkChildren(issue);
        } else {
            cascadeDeleteSubtasks(issue);
        }
        commentRepository.deleteAllByIssueId(issue.getId());
        issueRepository.delete(issue);
    }

    /** Deleting an Epic must not take its linked Stories/Tasks/Bugs down with it — see IssueService#delete. */
    private void unlinkChildren(Issue epic) {
        issueRepository.findAllByProjectIdAndParentIssueIdOrderByCreatedAtAsc(epic.getProjectId(), epic.getId())
                .forEach(child -> child.setParentIssueId(null));
    }

    /** A Subtask has no independent value once its parent is gone — its own comments go with it too. */
    private void cascadeDeleteSubtasks(Issue parent) {
        List<Issue> subtasks = issueRepository.findAllByParentIssueIdOrderByCreatedAtAsc(parent.getId());
        subtasks.forEach(subtask -> commentRepository.deleteAllByIssueId(subtask.getId()));
        issueRepository.deleteAll(subtasks);
    }

    @Override
    @Transactional
    public IssueResponse createSubtask(UUID callerId, String issueKey, CreateSubtaskRequest request) {
        Issue parent = requireIssue(issueKey);
        String projectKey = projectKeyOf(parent);
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);
        requireSubtaskableParentType(parent);
        if (request.assigneeId() != null) {
            requireAssigneeIsProjectMember(callerId, projectKey, request.assigneeId());
        }

        IssuePriority priority = request.priority() != null ? request.priority() : IssuePriority.MEDIUM;
        String description = request.description() == null ? null : request.description().trim();
        String subtaskKey = nextIssueKey(project.id(), project.key());
        double rank = nextBacklogRank(project.id());
        WorkflowSchemeResponse scheme = issueWorkflowAccess.requireScheme(callerId, projectKey);

        Issue subtask = new Issue(project.id(), subtaskKey, IssueType.SUBTASK, request.title().trim(), description,
                priority, request.assigneeId(), callerId, rank);
        subtask.setWorkflowStatusId(initialStatusId(scheme));
        subtask.setParentIssueId(parent.getId());
        Issue saved = issueRepository.save(subtask);
        return issueLabelingService.toResponse(saved, scheme, callerId, projectAccess.getRole(callerId, projectKey));
    }

    @Override
    @Transactional
    public List<IssueResponse> listSubtasks(UUID callerId, String issueKey) {
        Issue issue = requireIssue(issueKey);
        String projectKey = projectKeyOf(issue);
        projectAccess.requireMembership(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        return issueLabelingService.toResponses(
                issueRepository.findAllByParentIssueIdOrderByCreatedAtAsc(issue.getId()), callerId, projectKey, role);
    }

    /**
     * {@code doneCount} is a direct SQL count against {@code workflowStatusId IN (:doneStatusIds)}, not
     * a per-issue read — so a linked issue whose {@code workflowStatusId} predates this phase and hasn't
     * individually been read since (via its own {@code GET}, the board, etc.) still has a {@code null}
     * column value and won't match the {@code IN} clause, undercounting it until that one-time lazy
     * backfill runs elsewhere. Self-healing (the very next view of that issue anywhere backfills it) and
     * accepted for v1 — the same class of approximation as {@code BurndownServiceImpl}'s documented
     * "moved out of the sprint before this ran" limitation, not worth a batch migration when there's no
     * migration tool in this project to script one with.
     */
    @Override
    @Transactional
    public EpicProgressResponse getEpicProgress(UUID callerId, String epicKey) {
        Issue epic = requireIssue(epicKey);
        String projectKey = projectKeyOf(epic);
        projectAccess.requireMembership(callerId, projectKey);
        if (epic.getType() != IssueType.EPIC) {
            throw new NotAnEpicException(epic.getKey());
        }

        long total = issueRepository.countByProjectIdAndParentIssueId(epic.getProjectId(), epic.getId());
        List<UUID> doneStatusIds = issueWorkflowAccess.getStatusIdsInCategory(callerId, projectKey, StatusCategory.DONE);
        long done = doneStatusIds.isEmpty() ? 0L
                : issueRepository.countByProjectIdAndParentIssueIdAndWorkflowStatusIdIn(epic.getProjectId(),
                        epic.getId(), doneStatusIds);
        return EpicProgressResponse.of(total, done);
    }

    @Override
    @Transactional
    public IssueResponse moveToSprint(UUID callerId, String issueKey, UUID sprintId) {
        Issue issue = requireIssue(issueKey);
        String projectKey = projectKeyOf(issue);
        projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);

        issue.setSprintId(sprintId);
        return issueLabelingService.toResponse(issue, callerId, projectKey, projectAccess.getRole(callerId, projectKey));
    }

    @Override
    @Transactional
    public IssueResponse reorderBacklog(UUID callerId, String issueKey, String beforeIssueKey, String afterIssueKey) {
        Issue issue = requireIssue(issueKey);
        String projectKey = projectKeyOf(issue);
        projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);

        Optional<Issue> beforeIssue = findNeighbor(beforeIssueKey);
        Optional<Issue> afterIssue = findNeighbor(afterIssueKey);
        issue.setBacklogRank(newBacklogRank(issue, beforeIssue, afterIssue));
        return issueLabelingService.toResponse(issue, callerId, projectKey, projectAccess.getRole(callerId, projectKey));
    }

    @Override
    @Transactional
    public void moveUnfinishedIssuesToBacklog(UUID callerId, String projectKey, UUID sprintId) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        WorkflowSchemeResponse scheme = issueWorkflowAccess.requireScheme(callerId, projectKey);
        Set<UUID> doneStatusIds = scheme.statuses().stream()
                .filter(status -> status.category() == StatusCategory.DONE)
                .map(WorkflowStatusResponse::id)
                .collect(Collectors.toSet());

        issueRepository.findAllByProjectIdAndSprintId(project.id(), sprintId).forEach(issue -> {
            issueLabelingService.backfillWorkflowStatusIfMissing(issue, scheme);
            if (!doneStatusIds.contains(issue.getWorkflowStatusId())) {
                issue.setSprintId(null);
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsAnyWithWorkflowStatusId(UUID projectId, UUID workflowStatusId) {
        return issueRepository.existsByProjectIdAndWorkflowStatusId(projectId, workflowStatusId);
    }

    /**
     * Midpoint-insertion ranking: splits the difference between the two neighbors an issue is dropped
     * between, so re-ranking one issue never touches any other row. Falling off either end of the list
     * steps by a full {@link #RANK_STEP} past the remaining neighbor instead of halving toward it,
     * leaving room for further inserts on that side.
     */
    private double newBacklogRank(Issue issue, Optional<Issue> beforeIssue, Optional<Issue> afterIssue) {
        if (beforeIssue.isPresent() && afterIssue.isPresent()) {
            return (beforeIssue.get().getBacklogRank() + afterIssue.get().getBacklogRank()) / 2.0;
        }
        if (beforeIssue.isPresent()) {
            return beforeIssue.get().getBacklogRank() + RANK_STEP;
        }
        if (afterIssue.isPresent()) {
            return afterIssue.get().getBacklogRank() - RANK_STEP;
        }
        return issue.getBacklogRank();
    }

    private Optional<Issue> findNeighbor(String neighborKey) {
        return neighborKey == null ? Optional.empty() : issueRepository.findByKey(normalizeKey(neighborKey));
    }

    private double nextBacklogRank(UUID projectId) {
        return issueRepository.findFirstByProjectIdOrderByBacklogRankDesc(projectId)
                .map(issue -> issue.getBacklogRank() + RANK_STEP)
                .orElse(RANK_STEP);
    }

    private void requireOwnerOrAdmin(UUID callerId, String projectKey) {
        if (!projectAccess.isOwnerOrAdmin(callerId, projectKey)) {
            throw new InsufficientProjectRoleException();
        }
    }

    private void requireNotViewer(ProjectRole role) {
        if (role == ProjectRole.VIEWER) {
            throw new InsufficientProjectRoleException();
        }
    }

    /** Owner/Admin unrestricted; a Member may edit only an issue they're the assignee or reporter of; a Viewer never. */
    private void requireCanEditIssue(ProjectRole role, UUID callerId, Issue issue) {
        boolean ownerOrAdmin = role == ProjectRole.OWNER || role == ProjectRole.ADMIN;
        boolean involvedMember = role == ProjectRole.MEMBER
                && (callerId.equals(issue.getAssigneeId()) || callerId.equals(issue.getReporterId()));
        if (!ownerOrAdmin && !involvedMember) {
            throw new InsufficientProjectRoleException();
        }
    }

    private String nextIssueKey(UUID projectId, String projectKey) {
        IssueKeySequence sequence = issueKeySequenceRepository.findForUpdate(projectId)
                .orElseGet(() -> new IssueKeySequence(projectId));
        long number = sequence.incrementAndGet();
        issueKeySequenceRepository.save(sequence);
        return projectKey + "-" + number;
    }

    private Issue requireIssue(String issueKey) {
        return issueRepository.findByKey(normalizeKey(issueKey))
                .orElseThrow(() -> new IssueNotFoundException(issueKey));
    }

    /** Safe because a project key (validated at creation) never contains a hyphen — see CreateProjectRequest. */
    private String projectKeyOf(Issue issue) {
        return issue.getKey().substring(0, issue.getKey().lastIndexOf('-'));
    }

    private void requireAssigneeIsProjectMember(UUID callerId, String projectKey, UUID assigneeId) {
        if (!projectAccess.isMember(callerId, projectKey, assigneeId)) {
            throw new AssigneeNotAProjectMemberException();
        }
    }

    private String normalizeKey(String key) {
        return key.trim().toUpperCase(Locale.ROOT);
    }
}
