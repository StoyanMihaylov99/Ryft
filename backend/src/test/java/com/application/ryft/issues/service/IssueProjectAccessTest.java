package com.application.ryft.issues.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.application.ryft.issues.exception.NotAProjectMemberException;
import com.application.ryft.issues.exception.ProjectNotFoundException;
import com.application.ryft.projects.dto.ProjectMemberResponse;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.service.ProjectService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssueProjectAccessTest {

    @Mock
    private ProjectService projectService;

    private IssueProjectAccess projectAccess;

    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        projectAccess = new IssueProjectAccess(projectService);
    }

    @Test
    void requireMembershipReturnsProjectWhenCallerIsAMember() {
        ProjectResponse project = new ProjectResponse(UUID.randomUUID(), UUID.randomUUID(), "TRK", "Tracker", null,
                Instant.now(), null);
        when(projectService.get(callerId, "TRK")).thenReturn(project);

        assertThat(projectAccess.requireMembership(callerId, "TRK")).isEqualTo(project);
    }

    @Test
    void requireMembershipTranslatesProjectNotFound() {
        when(projectService.get(callerId, "TRK"))
                .thenThrow(new com.application.ryft.projects.exception.ProjectNotFoundException("TRK"));

        assertThatThrownBy(() -> projectAccess.requireMembership(callerId, "TRK"))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void requireMembershipTranslatesNotAProjectMember() {
        when(projectService.get(callerId, "TRK"))
                .thenThrow(new com.application.ryft.projects.exception.NotAProjectMemberException());

        assertThatThrownBy(() -> projectAccess.requireMembership(callerId, "TRK"))
                .isInstanceOf(NotAProjectMemberException.class);
    }

    @Test
    void requireMembershipTranslatesWorkspaceNotReady() {
        when(projectService.get(callerId, "TRK"))
                .thenThrow(new com.application.ryft.projects.exception.WorkspaceNotReadyException());

        assertThatThrownBy(() -> projectAccess.requireMembership(callerId, "TRK"))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void isOwnerOrAdminTrueForOwner() {
        when(projectService.getRole(callerId, "TRK")).thenReturn(java.util.Optional.of(ProjectRole.OWNER));

        assertThat(projectAccess.isOwnerOrAdmin(callerId, "TRK")).isTrue();
    }

    @Test
    void isOwnerOrAdminTrueForAdmin() {
        when(projectService.getRole(callerId, "TRK")).thenReturn(java.util.Optional.of(ProjectRole.ADMIN));

        assertThat(projectAccess.isOwnerOrAdmin(callerId, "TRK")).isTrue();
    }

    @Test
    void isOwnerOrAdminFalseForPlainMember() {
        when(projectService.getRole(callerId, "TRK")).thenReturn(java.util.Optional.of(ProjectRole.MEMBER));

        assertThat(projectAccess.isOwnerOrAdmin(callerId, "TRK")).isFalse();
    }

    @Test
    void isViewerTrueForViewer() {
        when(projectService.getRole(callerId, "TRK")).thenReturn(java.util.Optional.of(ProjectRole.VIEWER));

        assertThat(projectAccess.isViewer(callerId, "TRK")).isTrue();
    }

    @Test
    void isViewerFalseForMember() {
        when(projectService.getRole(callerId, "TRK")).thenReturn(java.util.Optional.of(ProjectRole.MEMBER));

        assertThat(projectAccess.isViewer(callerId, "TRK")).isFalse();
    }

    @Test
    void getRoleThrowsWhenCallerHasNoRole() {
        when(projectService.getRole(callerId, "TRK")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> projectAccess.getRole(callerId, "TRK"))
                .isInstanceOf(NotAProjectMemberException.class);
    }

    @Test
    void isMemberTrueWhenUserIsAmongProjectMembers() {
        UUID userId = UUID.randomUUID();
        when(projectService.listMembers(callerId, "TRK")).thenReturn(
                List.of(new ProjectMemberResponse(userId, "a@example.com", "A", null, ProjectRole.MEMBER, Instant.now())));

        assertThat(projectAccess.isMember(callerId, "TRK", userId)).isTrue();
    }

    @Test
    void isMemberFalseWhenUserIsNotAmongProjectMembers() {
        when(projectService.listMembers(callerId, "TRK")).thenReturn(List.of());

        assertThat(projectAccess.isMember(callerId, "TRK", UUID.randomUUID())).isFalse();
    }
}
