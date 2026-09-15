package com.application.ryft.workflow.service;

import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import com.application.ryft.workflow.entity.WorkflowScheme;
import com.application.ryft.workflow.entity.WorkflowStatus;
import com.application.ryft.workflow.repository.WorkflowSchemeRepository;
import com.application.ryft.workflow.repository.WorkflowStatusRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowSchemeRepository workflowSchemeRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final ProjectAccess projectAccess;

    public WorkflowServiceImpl(WorkflowSchemeRepository workflowSchemeRepository,
            WorkflowStatusRepository workflowStatusRepository, ProjectAccess projectAccess) {
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
        workflowStatusRepository.save(new WorkflowStatus(scheme, "In Progress", StatusCategory.IN_PROGRESS, 1));
        workflowStatusRepository.save(new WorkflowStatus(scheme, "Done", StatusCategory.DONE, 2));
        return scheme;
    }

    private WorkflowSchemeResponse toResponse(WorkflowScheme scheme, List<WorkflowStatus> statuses) {
        List<WorkflowStatusResponse> statusResponses = statuses.stream()
                .map(status -> new WorkflowStatusResponse(status.getId(), status.getName(), status.getCategory(),
                        status.getSortOrder()))
                .toList();
        return new WorkflowSchemeResponse(scheme.getId(), scheme.getProjectId(), scheme.getName(), statusResponses);
    }
}
