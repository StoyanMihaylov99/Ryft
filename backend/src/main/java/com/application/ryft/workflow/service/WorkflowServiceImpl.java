package com.application.ryft.workflow.service;

import com.application.ryft.issues.service.IssueService;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.workflow.dto.UpdateWorkflowSchemeRequest;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusEdit;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.dto.WorkflowTransitionEdit;
import com.application.ryft.workflow.dto.WorkflowTransitionResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import com.application.ryft.workflow.entity.WorkflowScheme;
import com.application.ryft.workflow.entity.WorkflowStatus;
import com.application.ryft.workflow.entity.WorkflowTransition;
import com.application.ryft.workflow.exception.DuplicateWorkflowTransitionException;
import com.application.ryft.workflow.exception.InsufficientProjectRoleException;
import com.application.ryft.workflow.exception.InvalidWorkflowStatusReferenceException;
import com.application.ryft.workflow.exception.WorkflowStatusInUseException;
import com.application.ryft.workflow.repository.WorkflowSchemeRepository;
import com.application.ryft.workflow.repository.WorkflowStatusRepository;
import com.application.ryft.workflow.repository.WorkflowTransitionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowSchemeRepository workflowSchemeRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final WorkflowProjectAccess projectAccess;
    private final IssueService issueService;

    /**
     * {@code issueService} is {@code @Lazy}: {@code issues.service.IssueServiceImpl} depends on
     * {@link WorkflowService} (for transition validation) and this class depends on {@link IssueService}
     * (only for {@link IssueService#existsAnyWithWorkflowStatusId}, the status-deletion guard) — a
     * legitimate two-way relationship through public service interfaces (see ARCHITECTURE.md's
     * module-dependency notes), but Spring's constructor injection still needs to build one concrete
     * bean before the other, so a plain constructor reference here would form a real
     * {@code BeanCurrentlyInCreationException} cycle at startup. {@code @Lazy} defers resolving the
     * actual {@code IssueServiceImpl} bean behind a proxy until this dependency is first used (i.e. the
     * first call to {@link #updateScheme}, well after both beans exist), breaking the cycle without
     * giving up constructor injection.
     */
    public WorkflowServiceImpl(WorkflowSchemeRepository workflowSchemeRepository,
            WorkflowStatusRepository workflowStatusRepository,
            WorkflowTransitionRepository workflowTransitionRepository, WorkflowProjectAccess projectAccess,
            @Lazy IssueService issueService) {
        this.workflowSchemeRepository = workflowSchemeRepository;
        this.workflowStatusRepository = workflowStatusRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
        this.projectAccess = projectAccess;
        this.issueService = issueService;
    }

    @Override
    @Transactional
    public WorkflowSchemeResponse getSchemeForProject(UUID callerId, String projectKey) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        WorkflowScheme scheme = workflowSchemeRepository.findByProjectId(project.id())
                .orElseGet(() -> createDefaultScheme(project.id()));
        List<WorkflowStatus> statuses = backfillBlockedStatusIfMissing(scheme, workflowStatusRepository
                .findAllByWorkflowSchemeIdOrderBySortOrderAsc(scheme.getId()));
        List<WorkflowTransition> transitions = backfillTransitionsIfMissing(scheme, statuses,
                workflowTransitionRepository.findAllByWorkflowSchemeId(scheme.getId()));
        return toResponse(scheme, statuses, transitions);
    }

    /**
     * Applies a full statuses+transitions diff in one call, since a scheme's statuses and transitions are
     * one interdependent graph, not independent flat entries like labels/components. Rules:
     * <ul>
     *   <li>A {@link WorkflowStatusEdit} with {@code id() == null} creates a status; one whose id matches
     *       an existing status renames/recategorizes/reorders it in place; an existing status whose id is
     *       absent from the submitted list is deleted — rejected with
     *       {@link WorkflowStatusInUseException} if any {@code Issue} still references it (checked via
     *       {@link IssueService#existsAnyWithWorkflowStatusId}, since {@code workflow} must not depend on
     *       {@code issues}' repository/entity — see ARCHITECTURE.md).</li>
     *   <li>A {@link WorkflowTransitionEdit} follows the identical create/keep/delete shape. Both
     *       {@code fromStatusId} and {@code toStatusId} must resolve to a status that already existed in
     *       this scheme <em>before</em> this call — a brand-new status and a transition touching it can't
     *       be added in the same {@code PATCH} (v1 simplification avoiding a two-pass id-resolution
     *       scheme, mirroring the already-accepted "one scheme per project" v1 constraint). Duplicate
     *       {@code (fromStatusId, toStatusId)} pairs, whether against another row in this same request or
     *       an existing untouched transition, are rejected with
     *       {@link DuplicateWorkflowTransitionException}.</li>
     * </ul>
     */
    @Override
    @Transactional
    public WorkflowSchemeResponse updateScheme(UUID callerId, String projectKey, UpdateWorkflowSchemeRequest request) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);
        WorkflowScheme scheme = workflowSchemeRepository.findByProjectId(project.id())
                .orElseGet(() -> createDefaultScheme(project.id()));

        Map<UUID, WorkflowStatus> statusesById = new LinkedHashMap<>();
        workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(scheme.getId())
                .forEach(status -> statusesById.put(status.getId(), status));
        Set<UUID> keptStatusIds = new HashSet<>();
        for (WorkflowStatusEdit edit : request.statuses()) {
            if (edit.id() != null) {
                keptStatusIds.add(edit.id());
            }
        }
        for (UUID existingId : Set.copyOf(statusesById.keySet())) {
            if (!keptStatusIds.contains(existingId)) {
                if (issueService.existsAnyWithWorkflowStatusId(project.id(), existingId)) {
                    throw new WorkflowStatusInUseException(statusesById.get(existingId).getName());
                }
                workflowTransitionRepository.deleteAllReferencingStatus(existingId);
                workflowStatusRepository.delete(statusesById.remove(existingId));
            }
        }
        for (WorkflowStatusEdit edit : request.statuses()) {
            if (edit.id() == null) {
                WorkflowStatus created = workflowStatusRepository
                        .save(new WorkflowStatus(scheme, edit.name().trim(), edit.category(), edit.sortOrder()));
                statusesById.put(created.getId(), created);
            } else {
                WorkflowStatus existing = statusesById.get(edit.id());
                if (existing == null) {
                    throw new InvalidWorkflowStatusReferenceException(edit.id());
                }
                existing.setName(edit.name().trim());
                existing.setCategory(edit.category());
                existing.setSortOrder(edit.sortOrder());
            }
        }

        Map<UUID, WorkflowTransition> transitionsById = new LinkedHashMap<>();
        workflowTransitionRepository.findAllByWorkflowSchemeId(scheme.getId())
                .forEach(transition -> transitionsById.put(transition.getId(), transition));
        Set<UUID> keptTransitionIds = new HashSet<>();
        for (WorkflowTransitionEdit edit : request.transitions()) {
            if (edit.id() != null) {
                keptTransitionIds.add(edit.id());
            }
        }
        for (UUID existingId : Set.copyOf(transitionsById.keySet())) {
            if (!keptTransitionIds.contains(existingId)) {
                workflowTransitionRepository.delete(transitionsById.remove(existingId));
            }
        }
        Set<String> seenPairsInRequest = new HashSet<>();
        for (WorkflowTransitionEdit edit : request.transitions()) {
            requireExistingStatus(keptStatusIds, edit.fromStatusId());
            requireExistingStatus(keptStatusIds, edit.toStatusId());
            String pairKey = edit.fromStatusId() + ":" + edit.toStatusId();
            if (!seenPairsInRequest.add(pairKey)) {
                throw new DuplicateWorkflowTransitionException(edit.fromStatusId(), edit.toStatusId());
            }

            WorkflowTransition existing = edit.id() == null ? null : transitionsById.get(edit.id());
            if (existing != null) {
                existing.setName(edit.name());
            } else {
                if (workflowTransitionRepository.existsByWorkflowSchemeIdAndFromStatusIdAndToStatusId(
                        scheme.getId(), edit.fromStatusId(), edit.toStatusId())) {
                    throw new DuplicateWorkflowTransitionException(edit.fromStatusId(), edit.toStatusId());
                }
                WorkflowTransition created = workflowTransitionRepository.save(new WorkflowTransition(scheme,
                        statusesById.get(edit.fromStatusId()), statusesById.get(edit.toStatusId()), edit.name()));
                transitionsById.put(created.getId(), created);
            }
        }

        List<WorkflowStatus> statuses = statusesById.values().stream()
                .sorted(Comparator.comparingInt(WorkflowStatus::getSortOrder))
                .toList();
        List<WorkflowTransition> transitions = List.copyOf(transitionsById.values());
        // Marks this scheme as deliberately configured, whether or not the submitted transitions list
        // ends up empty — see WorkflowScheme.transitionsInitialized's javadoc and
        // backfillTransitionsIfMissing below. Without this, the very next GET/status-change would read
        // zero transitions, mistake it for "never seeded," and silently re-seed any-to-any over an
        // admin's deliberate lockdown.
        scheme.setTransitionsInitialized(true);
        return toResponse(scheme, statuses, transitions);
    }

    @Override
    @Transactional
    public boolean isTransitionLegal(UUID callerId, String projectKey, UUID fromStatusId, UUID toStatusId) {
        if (fromStatusId.equals(toStatusId)) {
            return true;
        }
        WorkflowSchemeResponse scheme = getSchemeForProject(callerId, projectKey);
        return scheme.transitions().stream()
                .anyMatch(t -> t.fromStatusId().equals(fromStatusId) && t.toStatusId().equals(toStatusId));
    }

    @Override
    @Transactional
    public WorkflowStatusResponse getStatus(UUID callerId, String projectKey, UUID statusId) {
        WorkflowSchemeResponse scheme = getSchemeForProject(callerId, projectKey);
        return scheme.statuses().stream()
                .filter(status -> status.id().equals(statusId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No status " + statusId + " in project " + projectKey));
    }

    @Override
    @Transactional
    public List<UUID> getStatusIdsInCategory(UUID callerId, String projectKey, StatusCategory category) {
        WorkflowSchemeResponse scheme = getSchemeForProject(callerId, projectKey);
        return scheme.statuses().stream()
                .filter(status -> status.category() == category)
                .map(WorkflowStatusResponse::id)
                .toList();
    }

    private void requireOwnerOrAdmin(UUID callerId, String projectKey) {
        if (!projectAccess.isOwnerOrAdmin(callerId, projectKey)) {
            throw new InsufficientProjectRoleException();
        }
    }

    private void requireExistingStatus(Set<UUID> keptStatusIds, UUID statusId) {
        if (!keptStatusIds.contains(statusId)) {
            throw new InvalidWorkflowStatusReferenceException(statusId);
        }
    }

    /**
     * Creates the fixed default scheme for a project that doesn't have one yet. Two concurrent
     * first-requests for the same brand-new project could both miss the {@code findByProjectId} lookup
     * and both attempt to insert a scheme row — the unique constraint on {@code project_id} rejects the
     * second, an accepted narrow race for v1 (the failed request can simply be retried), the same
     * trade-off already made for {@code issues.entity.IssueKeySequence}'s first-row case. Transitions are
     * seeded separately by {@link #backfillTransitionsIfMissing}, called uniformly by every caller right
     * after this — see {@link #getSchemeForProject}.
     */
    private WorkflowScheme createDefaultScheme(UUID projectId) {
        WorkflowScheme scheme = workflowSchemeRepository.save(new WorkflowScheme(projectId, "Default Workflow"));
        workflowStatusRepository.save(new WorkflowStatus(scheme, "To Do", StatusCategory.TODO, 0));
        workflowStatusRepository.save(new WorkflowStatus(scheme, "Blocked", StatusCategory.BLOCKED, 1));
        workflowStatusRepository.save(new WorkflowStatus(scheme, "In Progress", StatusCategory.IN_PROGRESS, 2));
        workflowStatusRepository.save(new WorkflowStatus(scheme, "Done", StatusCategory.DONE, 3));
        return scheme;
    }

    private List<WorkflowStatus> backfillBlockedStatusIfMissing(WorkflowScheme scheme, List<WorkflowStatus> statuses) {
        if (statuses.stream().anyMatch(status -> status.getCategory() == StatusCategory.BLOCKED)) {
            return statuses;
        }
        int insertAt = statuses.stream()
                .filter(status -> status.getCategory() == StatusCategory.IN_PROGRESS)
                .mapToInt(WorkflowStatus::getSortOrder)
                .findFirst()
                .orElse(statuses.size());

        List<WorkflowStatus> updated = new ArrayList<>(statuses.size() + 1);
        for (WorkflowStatus status : statuses) {
            if (status.getSortOrder() >= insertAt) {
                status.setSortOrder(status.getSortOrder() + 1);
            }
            updated.add(status);
        }
        updated.add(workflowStatusRepository.save(new WorkflowStatus(scheme, "Blocked", StatusCategory.BLOCKED, insertAt)));
        updated.sort(Comparator.comparingInt(WorkflowStatus::getSortOrder));
        return updated;
    }

    /**
     * Seeds a full any-to-any transition graph (every status &rarr; every other status) the first time a
     * scheme's transitions have never been deliberately initialized — both for a brand-new scheme (via
     * {@link #createDefaultScheme}, whose caller always calls this right after) and, critically, for
     * every <em>existing</em> project's scheme the first time it's read post-deploy. Any-to-any, not a
     * curated "logical" adjacency set, because turning on enforcement must not regress today's behavior
     * (any member can currently drag a card to any column) — admins can narrow it afterward via
     * {@link #updateScheme} if they want stricter process.
     *
     * <p>Gated on {@link WorkflowScheme#getTransitionsInitialized()}, not {@code transitions.isEmpty()}:
     * an empty list is ambiguous on its own — it's true both for a scheme that's never been seeded and
     * for one where an Owner/Admin deliberately submitted {@code transitions: []} via {@link #updateScheme}
     * to lock the board down, and re-seeding any-to-any over that deliberate choice on the very next read
     * would silently discard it with no error or log. A scheme that already has transitions but a
     * {@code null}/{@code false} flag (any scheme seeded before this flag existed) is treated as already
     * initialized too — lazily repairing the flag rather than re-seeding on top of rows that already
     * exist, the same one-time lazy-repair pattern used for {@code Issue.workflowStatusId}.
     */
    private List<WorkflowTransition> backfillTransitionsIfMissing(WorkflowScheme scheme, List<WorkflowStatus> statuses,
            List<WorkflowTransition> transitions) {
        if (Boolean.TRUE.equals(scheme.getTransitionsInitialized())) {
            return transitions;
        }
        if (!transitions.isEmpty()) {
            scheme.setTransitionsInitialized(true);
            return transitions;
        }
        List<WorkflowTransition> seeded = new ArrayList<>();
        for (WorkflowStatus from : statuses) {
            for (WorkflowStatus to : statuses) {
                if (!from.getId().equals(to.getId())) {
                    seeded.add(workflowTransitionRepository
                            .save(new WorkflowTransition(scheme, from, to, from.getName() + " → " + to.getName())));
                }
            }
        }
        scheme.setTransitionsInitialized(true);
        return seeded;
    }

    private WorkflowSchemeResponse toResponse(WorkflowScheme scheme, List<WorkflowStatus> statuses,
            List<WorkflowTransition> transitions) {
        List<WorkflowStatusResponse> statusResponses = statuses.stream()
                .map(status -> new WorkflowStatusResponse(status.getId(), status.getName(), status.getCategory(),
                        status.getSortOrder()))
                .toList();
        List<WorkflowTransitionResponse> transitionResponses = transitions.stream()
                .map(t -> new WorkflowTransitionResponse(t.getId(), t.getFromStatus().getId(), t.getFromStatus().getName(),
                        t.getToStatus().getId(), t.getToStatus().getName(), t.getName()))
                .toList();
        return new WorkflowSchemeResponse(scheme.getId(), scheme.getProjectId(), scheme.getName(), statusResponses,
                transitionResponses);
    }
}
