package com.application.ryft.issues.service;

import com.application.ryft.issues.exception.NotAProjectMemberException;
import com.application.ryft.issues.exception.ProjectNotFoundException;
import com.application.ryft.projects.dto.ProjectResponse;
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
        }
    }

    boolean isMember(UUID callerId, String projectKey, UUID userId) {
        return projectService.listMembers(callerId, projectKey).stream()
                .anyMatch(member -> member.userId().equals(userId));
    }
}
