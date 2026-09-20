package com.application.ryft.issues.service;

import com.application.ryft.issues.exception.NotAProjectMemberException;
import com.application.ryft.issues.exception.ProjectNotFoundException;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import com.application.ryft.workflow.service.WorkflowService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link WorkflowService} for every issues-module service that needs the project's workflow
 * scheme or transition rules — {@link IssueServiceImpl} (transition validation, status resolution) and
 * {@link IssueLabelingService} (batched status resolution for {@code IssueResponse}). Centralizes the
 * cross-module exception translation the same way {@link IssueProjectAccess} centralizes it for
 * {@code ProjectService} — see that class's javadoc for why this can't just be left to each call site.
 * Package-private and named per-module for the same bean-naming-collision reason documented there.
 */
@Component
class IssueWorkflowAccess {

    private final WorkflowService workflowService;

    IssueWorkflowAccess(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    WorkflowSchemeResponse requireScheme(UUID callerId, String projectKey) {
        try {
            return workflowService.getSchemeForProject(callerId, projectKey);
        } catch (com.application.ryft.workflow.exception.ProjectNotFoundException e) {
            throw new ProjectNotFoundException(projectKey);
        } catch (com.application.ryft.workflow.exception.NotAProjectMemberException e) {
            throw new NotAProjectMemberException();
        }
    }

    boolean isTransitionLegal(UUID callerId, String projectKey, UUID fromStatusId, UUID toStatusId) {
        try {
            return workflowService.isTransitionLegal(callerId, projectKey, fromStatusId, toStatusId);
        } catch (com.application.ryft.workflow.exception.ProjectNotFoundException e) {
            throw new ProjectNotFoundException(projectKey);
        } catch (com.application.ryft.workflow.exception.NotAProjectMemberException e) {
            throw new NotAProjectMemberException();
        }
    }

    WorkflowStatusResponse getStatus(UUID callerId, String projectKey, UUID statusId) {
        try {
            return workflowService.getStatus(callerId, projectKey, statusId);
        } catch (com.application.ryft.workflow.exception.ProjectNotFoundException e) {
            throw new ProjectNotFoundException(projectKey);
        } catch (com.application.ryft.workflow.exception.NotAProjectMemberException e) {
            throw new NotAProjectMemberException();
        }
    }

    List<UUID> getStatusIdsInCategory(UUID callerId, String projectKey, StatusCategory category) {
        try {
            return workflowService.getStatusIdsInCategory(callerId, projectKey, category);
        } catch (com.application.ryft.workflow.exception.ProjectNotFoundException e) {
            throw new ProjectNotFoundException(projectKey);
        } catch (com.application.ryft.workflow.exception.NotAProjectMemberException e) {
            throw new NotAProjectMemberException();
        }
    }
}
