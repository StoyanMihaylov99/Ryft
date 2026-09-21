package com.application.ryft.notifications.service;

import com.application.ryft.projects.dto.ProjectMemberResponse;
import com.application.ryft.projects.service.ProjectService;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link ProjectService} for the one cross-module lookup {@code notifications} needs: resolving
 * {@code @mention} emails to member ids. Named per-module (not just "ProjectAccess") for the same
 * bean-naming-collision reason documented on {@code issues.service.IssueProjectAccess} — Spring's
 * default component-scan bean naming ignores the package, so a same-named {@code @Component} in
 * another module would collide at context startup.
 */
@Component
class NotificationsProjectAccess {

    private final ProjectService projectService;

    NotificationsProjectAccess(ProjectService projectService) {
        this.projectService = projectService;
    }

    /** Lowercased email -> member id, for every member of the project — used to resolve @mentions. */
    Map<String, UUID> listMemberIdsByEmail(UUID actorId, String projectKey) {
        return projectService.listMembers(actorId, projectKey).stream()
                .collect(Collectors.toMap(member -> member.email().toLowerCase(Locale.ROOT),
                        ProjectMemberResponse::userId, (first, second) -> first, LinkedHashMap::new));
    }
}
