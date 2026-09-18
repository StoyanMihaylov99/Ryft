package com.application.ryft.sprints.service;

import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.service.ProjectService;
import com.application.ryft.sprints.exception.NotAProjectMemberException;
import com.application.ryft.sprints.exception.ProjectNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link ProjectService} for every sprints-module service that needs to confirm project
 * membership. Centralizes the cross-module exception translation, mirroring
 * {@code issues.service.IssueProjectAccess} / {@code workflow.service.WorkflowProjectAccess}. Named
 * per-module (rather than just "ProjectAccess") because two same-named {@code @Component} classes in
 * different packages collide under Spring's default bean naming, which ignores the package.
 */
@Component
class SprintsProjectAccess {

    private final ProjectService projectService;

    SprintsProjectAccess(ProjectService projectService) {
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

    ProjectResponse requireMembershipByProjectId(UUID callerId, UUID projectId) {
        try {
            return projectService.getById(callerId, projectId);
        } catch (com.application.ryft.projects.exception.ProjectNotFoundException e) {
            throw new ProjectNotFoundException(projectId.toString());
        } catch (com.application.ryft.projects.exception.NotAProjectMemberException e) {
            throw new NotAProjectMemberException();
        } catch (com.application.ryft.projects.exception.WorkspaceNotReadyException e) {
            // No workspace at all yet means no project can exist either — same 404 as ProjectNotFoundException.
            throw new ProjectNotFoundException(projectId.toString());
        }
    }

    /** Owner/Admin manage sprints (create/edit); every other role only views them. */
    boolean isOwnerOrAdmin(UUID callerId, String projectKey) {
        return projectService.listMembers(callerId, projectKey).stream()
                .filter(member -> member.userId().equals(callerId))
                .anyMatch(member -> member.role() == ProjectRole.OWNER || member.role() == ProjectRole.ADMIN);
    }
}
