package com.application.ryft.sprints.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.service.IssueService;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.entity.SprintState;
import com.application.ryft.sprints.exception.CannotMoveIssueIntoCompletedSprintException;
import com.application.ryft.sprints.exception.InsufficientProjectRoleException;
import com.application.ryft.sprints.exception.IssueNotFoundException;
import com.application.ryft.sprints.exception.IssueSprintProjectMismatchException;
import com.application.ryft.sprints.exception.NotAProjectMemberException;
import com.application.ryft.sprints.exception.ProjectNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BacklogServiceImplTest {

    @Mock
    private IssueService issueService;

    @Mock
    private SprintsProjectAccess projectAccess;

    @Mock
    private SprintLookupSupport sprintLookupSupport;

    private BacklogServiceImpl backlogService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);

    @BeforeEach
    void setUp() {
        backlogService = new BacklogServiceImpl(issueService, projectAccess, sprintLookupSupport);
    }

    private IssueResponse issue(UUID sprintId) {
        return new IssueResponse(UUID.randomUUID(), projectId, "TRK-1", IssueType.BUG, "Title", null,
                IssueStatus.TODO, IssuePriority.MEDIUM, null, callerId, sprintId, null, null, Instant.now(), null, null);
    }

    @Test
    void listBacklogDelegatesToIssueService() {
        List<IssueResponse> expected = List.of(issue(null));
        when(issueService.listBacklogForProject(callerId, "TRK")).thenReturn(expected);

        List<IssueResponse> result = backlogService.listBacklog(callerId, "TRK");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void moveIssueToSprintSucceedsForOwnerOrAdmin() {
        UUID sprintId = UUID.randomUUID();
        when(issueService.get(callerId, "TRK-1")).thenReturn(issue(null));
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, null, null);
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(issueService.moveToSprint(callerId, "TRK-1", sprintId)).thenReturn(issue(sprintId));

        IssueResponse result = backlogService.moveIssue(callerId, "TRK-1", sprintId);

        assertThat(result.sprintId()).isEqualTo(sprintId);
    }

    @Test
    void moveIssueToBacklogSucceedsForOwnerOrAdmin() {
        UUID sprintId = UUID.randomUUID();
        when(issueService.get(callerId, "TRK-1")).thenReturn(issue(sprintId));
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueService.moveToSprint(callerId, "TRK-1", null)).thenReturn(issue(null));

        IssueResponse result = backlogService.moveIssue(callerId, "TRK-1", null);

        assertThat(result.sprintId()).isNull();
        verify(sprintLookupSupport, never()).requireSprint(any());
    }

    @Test
    void moveIssueIntoCompletedSprintThrows() {
        UUID sprintId = UUID.randomUUID();
        when(issueService.get(callerId, "TRK-1")).thenReturn(issue(null));
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, null, null);
        sprint.setState(SprintState.COMPLETED);
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);

        assertThatThrownBy(() -> backlogService.moveIssue(callerId, "TRK-1", sprintId))
                .isInstanceOf(CannotMoveIssueIntoCompletedSprintException.class);
        verify(issueService, never()).moveToSprint(any(), any(), any());
    }

    @Test
    void moveIssueIntoSprintFromDifferentProjectThrows() {
        UUID sprintId = UUID.randomUUID();
        when(issueService.get(callerId, "TRK-1")).thenReturn(issue(null));
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        Sprint sprint = new Sprint(UUID.randomUUID(), "Sprint 1", null, null, null);
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);

        assertThatThrownBy(() -> backlogService.moveIssue(callerId, "TRK-1", sprintId))
                .isInstanceOf(IssueSprintProjectMismatchException.class);
        verify(issueService, never()).moveToSprint(any(), any(), any());
    }

    @Test
    void moveIssueRejectsCallerWhoIsNotOwnerOrAdmin() {
        when(issueService.get(callerId, "TRK-1")).thenReturn(issue(null));
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> backlogService.moveIssue(callerId, "TRK-1", UUID.randomUUID()))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(issueService, never()).moveToSprint(any(), any(), any());
    }

    @Test
    void moveIssueTranslatesIssueNotFoundFromMoveToSprint() {
        when(issueService.get(callerId, "TRK-1")).thenReturn(issue(null));
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueService.moveToSprint(callerId, "TRK-1", null))
                .thenThrow(new com.application.ryft.issues.exception.IssueNotFoundException("TRK-1"));

        assertThatThrownBy(() -> backlogService.moveIssue(callerId, "TRK-1", null))
                .isInstanceOf(IssueNotFoundException.class);
    }

    @Test
    void moveIssueTranslatesProjectNotFoundFromMoveToSprint() {
        when(issueService.get(callerId, "TRK-1")).thenReturn(issue(null));
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueService.moveToSprint(callerId, "TRK-1", null))
                .thenThrow(new com.application.ryft.issues.exception.ProjectNotFoundException("TRK-1"));

        assertThatThrownBy(() -> backlogService.moveIssue(callerId, "TRK-1", null))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void moveIssueTranslatesNotAProjectMemberFromMoveToSprint() {
        when(issueService.get(callerId, "TRK-1")).thenReturn(issue(null));
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueService.moveToSprint(callerId, "TRK-1", null))
                .thenThrow(new com.application.ryft.issues.exception.NotAProjectMemberException());

        assertThatThrownBy(() -> backlogService.moveIssue(callerId, "TRK-1", null))
                .isInstanceOf(NotAProjectMemberException.class);
    }

    @Test
    void moveIssueTranslatesInsufficientProjectRoleFromMoveToSprint() {
        when(issueService.get(callerId, "TRK-1")).thenReturn(issue(null));
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueService.moveToSprint(callerId, "TRK-1", null))
                .thenThrow(new com.application.ryft.issues.exception.InsufficientProjectRoleException());

        assertThatThrownBy(() -> backlogService.moveIssue(callerId, "TRK-1", null))
                .isInstanceOf(InsufficientProjectRoleException.class);
    }
}
