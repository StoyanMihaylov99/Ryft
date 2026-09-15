package com.application.ryft.workflow.service;

import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.service.ProjectService;
import com.application.ryft.workflow.exception.NotAProjectMemberException;
import com.application.ryft.workflow.exception.ProjectNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link ProjectService} for this module's cross-module exception translation — same role as
 * {@code issues.service.ProjectAccess}. Kept as a separate copy per module (rather than a shared class)
 * because each module's translated exceptions belong to that module's own package, so its
 * {@code @RestControllerAdvice} can catch them; there's nothing meaningful to share beyond the shape.
 */
@Component
class ProjectAccess {

    private final ProjectService projectService;

    ProjectAccess(ProjectService projectService) {
        this.projectService = projectService;
    }

    ProjectResponse requireMembership(UUID callerId, String projectKey) {
        try {
            return projectService.get(callerId, projectKey);
        } catch (com.application.ryft.projects.exception.ProjectNotFoundException e) {
            throw new ProjectNotFoundException(projectKey);
        } catch (com.application.ryft.projects.exception.NotAProjectMemberException e) {
            throw new NotAProjectMemberException();
        }
    }
}
