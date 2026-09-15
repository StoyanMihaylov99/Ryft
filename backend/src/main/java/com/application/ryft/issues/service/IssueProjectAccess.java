package com.application.ryft.issues.service;

import com.application.ryft.issues.exception.NotAProjectMemberException;
import com.application.ryft.issues.exception.ProjectNotFoundException;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.service.ProjectService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link ProjectService} for every issues-module service that needs to confirm project
 * membership. Centralizes the cross-module exception translation (see
 * {@link ProjectNotFoundException}'s javadoc for why that translation has to happen at all) so it
 * can't silently drift between callers as more issue-adjacent services (comments, labels, ...) are
 * added. Named per-module (rather than just "ProjectAccess") because {@code workflow.service} has an
 * equivalent class for the same reason: two same-named {@code @Component} classes in different
 * packages would otherwise collide under Spring's default bean naming (which ignores the package).
 */
@Component
class IssueProjectAccess {

    private final ProjectService projectService;

    IssueProjectAccess(ProjectService projectService) {
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

    boolean isMember(UUID callerId, String projectKey, UUID userId) {
        return projectService.listMembers(callerId, projectKey).stream()
                .anyMatch(member -> member.userId().equals(userId));
    }

    /** Owner/Admin manage issues (create/edit/delete); every other role only comments and changes status. */
    boolean isOwnerOrAdmin(UUID callerId, String projectKey) {
        return projectService.listMembers(callerId, projectKey).stream()
                .filter(member -> member.userId().equals(callerId))
                .anyMatch(member -> member.role() == ProjectRole.OWNER || member.role() == ProjectRole.ADMIN);
    }
}
