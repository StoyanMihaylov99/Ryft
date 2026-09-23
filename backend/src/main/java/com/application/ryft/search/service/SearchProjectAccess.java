package com.application.ryft.search.service;

import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.service.ProjectService;
import com.application.ryft.search.exception.NotAProjectMemberException;
import com.application.ryft.search.exception.ProjectNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link ProjectService} for {@link SavedFilterServiceImpl} — saved filters are their own resource
 * with nothing to do with individual issues, so this goes straight to {@code projects}, not through
 * {@code issues.service.IssueService} the way {@code ProjectSearchServiceImpl} does for the (pre-existing,
 * unrelated) structured-search endpoint. Centralizes the cross-module exception translation, mirroring
 * {@code issues.service.IssueProjectAccess} / {@code sprints.service.SprintsProjectAccess}. Named
 * per-module (rather than just "ProjectAccess") because two same-named {@code @Component} classes in
 * different packages collide under Spring's default bean naming, which ignores the package.
 */
@Component
class SearchProjectAccess {

    private final ProjectService projectService;

    SearchProjectAccess(ProjectService projectService) {
        this.projectService = projectService;
    }

    ProjectResponse requireMembership(UUID callerId, String projectKey) {
        try {
            return projectService.get(callerId, projectKey);
        } catch (com.application.ryft.projects.exception.ProjectNotFoundException e) {
            throw new ProjectNotFoundException(projectKey);
        } catch (com.application.ryft.projects.exception.NotAProjectMemberException e) {
            throw new NotAProjectMemberException();
        } catch (com.application.ryft.projects.exception.WorkspaceNotReadyException e) {
            // No workspace at all yet means no project can exist either — same 404 as ProjectNotFoundException.
            throw new ProjectNotFoundException(projectKey);
        }
    }

    boolean isViewer(UUID callerId, String projectKey) {
        return projectService.getRole(callerId, projectKey)
                .map(role -> role == ProjectRole.VIEWER)
                .orElse(false);
    }
}
