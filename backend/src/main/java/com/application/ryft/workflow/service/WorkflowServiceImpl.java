package com.application.ryft.workflow.service;

import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import com.application.ryft.workflow.entity.WorkflowScheme;
import com.application.ryft.workflow.entity.WorkflowStatus;
import com.application.ryft.workflow.repository.WorkflowSchemeRepository;
import com.application.ryft.workflow.repository.WorkflowStatusRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowSchemeRepository workflowSchemeRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowProjectAccess projectAccess;

    public WorkflowServiceImpl(WorkflowSchemeRepository workflowSchemeRepository,
            WorkflowStatusRepository workflowStatusRepository, WorkflowProjectAccess projectAccess) {
        this.workflowSchemeRepository = workflowSchemeRepository;
        this.workflowStatusRepository = workflowStatusRepository;
        this.projectAccess = projectAccess;
    }

    @Override
    @Transactional
    public WorkflowSchemeResponse getSchemeForProject(UUID callerId, String projectKey) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        WorkflowScheme scheme = workflowSchemeRepository.findByProjectId(project.id())
                .orElseGet(() -> createDefaultScheme(project.id()));
        List<WorkflowStatus> statuses = workflowStatusRepository
                .findAllByWorkflowSchemeIdOrderBySortOrderAsc(scheme.getId());
        statuses = backfillBlockedStatusIfMissing(scheme, statuses);
        return toResponse(scheme, statuses);
    }

    /**
     * Creates the fixed default scheme for a project that doesn't have one yet. Two concurrent
     * first-requests for the same brand-new project could both miss the {@code findByProjectId} lookup
     * and both attempt to insert a scheme row — the unique constraint on {@code project_id} rejects the
     * second, an accepted narrow race for v1 (the failed request can simply be retried), the same
     * trade-off already made for {@code issues.entity.IssueKeySequence}'s first-row case.
     */
    private WorkflowScheme createDefaultScheme(UUID projectId) {
        WorkflowScheme scheme = workflowSchemeRepository.save(new WorkflowScheme(projectId, "Default Workflow"));
        workflowStatusRepository.save(new WorkflowStatus(scheme, "To Do", StatusCategory.TODO, 0));
        workflowStatusRepository.save(new WorkflowStatus(scheme, "Blocked", StatusCategory.BLOCKED, 1));
        workflowStatusRepository.save(new WorkflowStatus(scheme, "In Progress", StatusCategory.IN_PROGRESS, 2));
        workflowStatusRepository.save(new WorkflowStatus(scheme, "Done", StatusCategory.DONE, 3));
        return scheme;
    }

    /**
     * Schemes created before the Blocked status existed won't have one — there's no migration
     * framework in this project (Hibernate {@code ddl-auto: update} manages the schema), so existing
     * rows are repaired lazily on next read instead, the same pattern already used for creating the
     * scheme itself. Every status at or after "In Progress" shifts one sortOrder slot to make room;
     * the shift is picked up by JPA dirty checking (these are managed entities within this method's
     * transaction), only the new row needs an explicit save.
     *
     * <p>Note this backfill alone isn't sufficient on a database whose {@code workflow_statuses} table
     * was already created before this change: Hibernate auto-generates a CHECK constraint listing an
     * enum's values at table-creation time, and {@code ddl-auto: update} never widens an existing
     * constraint. {@code WorkflowStatus.category}'s {@code columnDefinition} now suppresses that
     * constraint going forward, but an already-created table needs its stale constraint dropped once,
     * by hand (see the migration note in DATA_MODEL.md).
     */
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

    private WorkflowSchemeResponse toResponse(WorkflowScheme scheme, List<WorkflowStatus> statuses) {
        List<WorkflowStatusResponse> statusResponses = statuses.stream()
                .map(status -> new WorkflowStatusResponse(status.getId(), status.getName(), status.getCategory(),
                        status.getSortOrder()))
                .toList();
        return new WorkflowSchemeResponse(scheme.getId(), scheme.getProjectId(), scheme.getName(), statusResponses);
    }
}
