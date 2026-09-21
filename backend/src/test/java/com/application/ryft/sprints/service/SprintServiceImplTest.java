package com.application.ryft.sprints.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.common.event.SprintCompletedEvent;
import com.application.ryft.common.event.SprintStartedEvent;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.service.IssueService;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.sprints.dto.CreateSprintRequest;
import com.application.ryft.sprints.dto.SprintResponse;
import com.application.ryft.sprints.dto.UpdateSprintRequest;
import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.entity.SprintState;
import com.application.ryft.sprints.exception.ActiveSprintAlreadyExistsException;
import com.application.ryft.sprints.exception.IllegalSprintStateTransitionException;
import com.application.ryft.sprints.exception.InsufficientProjectRoleException;
import com.application.ryft.sprints.exception.InvalidSprintDateRangeException;
import com.application.ryft.sprints.exception.SprintCompletedException;
import com.application.ryft.sprints.exception.SprintDatesRequiredException;
import com.application.ryft.sprints.repository.SprintRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SprintServiceImplTest {

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private SprintsProjectAccess projectAccess;

    @Mock
    private SprintLookupSupport sprintLookupSupport;

    @Mock
    private IssueService issueService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SprintServiceImpl sprintService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);

    @BeforeEach
    void setUp() {
        sprintService = new SprintServiceImpl(sprintRepository, projectAccess, sprintLookupSupport, issueService,
                eventPublisher);
    }

    @Test
    void createSavesSprintForOwnerOrAdmin() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(sprintRepository.save(any(Sprint.class))).thenAnswer(inv -> inv.getArgument(0));

        SprintResponse result = sprintService.create(callerId, "TRK",
                new CreateSprintRequest("Sprint 1", "Ship the thing", null, null));

        assertThat(result.name()).isEqualTo("Sprint 1");
        assertThat(result.goal()).isEqualTo("Ship the thing");
        assertThat(result.state()).isEqualTo(SprintState.PLANNED);
        assertThat(result.projectId()).isEqualTo(projectId);
    }

    @Test
    void createRejectsCallerWhoIsNotOwnerOrAdmin() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> sprintService.create(callerId, "TRK",
                new CreateSprintRequest("Sprint 1", null, null, null)))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(sprintRepository, never()).save(any());
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        LocalDate start = LocalDate.of(2026, 1, 10);
        LocalDate end = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> sprintService.create(callerId, "TRK",
                new CreateSprintRequest("Sprint 1", null, start, end)))
                .isInstanceOf(InvalidSprintDateRangeException.class);
        verify(sprintRepository, never()).save(any());
    }

    @Test
    void listForProjectReturnsProjectsSprints() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, null, null);
        when(sprintRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(List.of(sprint));

        List<SprintResponse> result = sprintService.listForProject(callerId, "TRK");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Sprint 1");
    }

    @Test
    void updateAppliesEachFieldIndependently() {
        Sprint sprint = new Sprint(projectId, "Original", "Original goal", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 14));
        when(sprintLookupSupport.requireSprint(any())).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        SprintResponse result = sprintService.update(callerId, UUID.randomUUID(),
                new UpdateSprintRequest("Renamed", null, null, null));

        assertThat(result.name()).isEqualTo("Renamed");
        assertThat(result.goal()).isEqualTo("Original goal");
    }

    @Test
    void updateAppliesAllFieldsWhenCombined() {
        Sprint sprint = new Sprint(projectId, "Original", "Original goal", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 14));
        when(sprintLookupSupport.requireSprint(any())).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        LocalDate newStart = LocalDate.of(2026, 2, 1);
        LocalDate newEnd = LocalDate.of(2026, 2, 14);
        SprintResponse result = sprintService.update(callerId, UUID.randomUUID(),
                new UpdateSprintRequest("Renamed", "New goal", newStart, newEnd));

        assertThat(result.name()).isEqualTo("Renamed");
        assertThat(result.goal()).isEqualTo("New goal");
        assertThat(result.startDate()).isEqualTo(newStart);
        assertThat(result.endDate()).isEqualTo(newEnd);
    }

    @Test
    void updateValidatesNewEndDateAgainstExistingStartDate() {
        Sprint sprint = new Sprint(projectId, "Original", null, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20));
        when(sprintLookupSupport.requireSprint(any())).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> sprintService.update(callerId, UUID.randomUUID(),
                new UpdateSprintRequest(null, null, null, LocalDate.of(2026, 1, 5))))
                .isInstanceOf(InvalidSprintDateRangeException.class);
    }

    @Test
    void updateOnCompletedSprintThrows() {
        Sprint sprint = new Sprint(projectId, "Original", null, null, null);
        sprint.setState(SprintState.COMPLETED);
        when(sprintLookupSupport.requireSprint(any())).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> sprintService.update(callerId, UUID.randomUUID(),
                new UpdateSprintRequest("New name", null, null, null)))
                .isInstanceOf(SprintCompletedException.class);
    }

    @Test
    void updateRejectsCallerWhoIsNotOwnerOrAdmin() {
        Sprint sprint = new Sprint(projectId, "Original", null, null, null);
        when(sprintLookupSupport.requireSprint(any())).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> sprintService.update(callerId, UUID.randomUUID(),
                new UpdateSprintRequest("New name", null, null, null)))
                .isInstanceOf(InsufficientProjectRoleException.class);
    }

    private IssueResponse issue(Integer storyPoints, IssueStatus status) {
        return new IssueResponse(UUID.randomUUID(), projectId, "TRK-1", IssueType.TASK, "Title", null, status,
                IssuePriority.MEDIUM, null, callerId, UUID.randomUUID(), storyPoints, null, List.of(), List.of(),
                Instant.now(), null, null);
    }

    @Test
    void startActivatesSprintAndSumsStoryPointsTreatingNullAsZero() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14));
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueService.listForSprint(callerId, "TRK", sprintId)).thenReturn(
                List.of(issue(5, IssueStatus.TODO), issue(null, IssueStatus.TODO), issue(3, IssueStatus.DONE)));

        SprintResponse result = sprintService.start(callerId, sprintId);

        assertThat(result.state()).isEqualTo(SprintState.ACTIVE);
        assertThat(result.committedPoints()).isEqualTo(8);
        ArgumentCaptor<SprintStartedEvent> captor = ArgumentCaptor.forClass(SprintStartedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sprintId()).isEqualTo(sprintId);
        assertThat(captor.getValue().projectKey()).isEqualTo("TRK");
        assertThat(captor.getValue().sprintName()).isEqualTo("Sprint 1");
    }

    @Test
    void startBlockedWhenAnotherSprintAlreadyActive() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14));
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(sprintRepository.existsByProjectIdAndState(projectId, SprintState.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> sprintService.start(callerId, sprintId))
                .isInstanceOf(ActiveSprintAlreadyExistsException.class);
        assertThat(sprint.getState()).isEqualTo(SprintState.PLANNED);
        verify(issueService, never()).listForSprint(any(), any(), any());
    }

    @Test
    void startBlockedWhenDatesMissing() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, null, null);
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> sprintService.start(callerId, sprintId))
                .isInstanceOf(SprintDatesRequiredException.class);
        assertThat(sprint.getState()).isEqualTo(SprintState.PLANNED);
    }

    @Test
    void startBlockedWhenSprintIsNotPlanned() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14));
        sprint.setState(SprintState.ACTIVE);
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> sprintService.start(callerId, sprintId))
                .isInstanceOf(IllegalSprintStateTransitionException.class);
    }

    @Test
    void startRejectsCallerWhoIsNotOwnerOrAdmin() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14));
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> sprintService.start(callerId, sprintId))
                .isInstanceOf(InsufficientProjectRoleException.class);
    }

    @Test
    void completeMarksCompletedAndMovesUnfinishedIssuesToBacklog() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14));
        sprint.setState(SprintState.ACTIVE);
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        SprintResponse result = sprintService.complete(callerId, sprintId);

        assertThat(result.state()).isEqualTo(SprintState.COMPLETED);
        assertThat(result.completedAt()).isNotNull();
        verify(issueService).moveUnfinishedIssuesToBacklog(callerId, "TRK", sprintId);
        ArgumentCaptor<SprintCompletedEvent> captor = ArgumentCaptor.forClass(SprintCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sprintId()).isEqualTo(sprintId);
        assertThat(captor.getValue().projectKey()).isEqualTo("TRK");
        assertThat(captor.getValue().sprintName()).isEqualTo("Sprint 1");
    }

    @Test
    void startDoesNotPublishEventWhenBlockedByAnotherActiveSprint() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14));
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(sprintRepository.existsByProjectIdAndState(projectId, SprintState.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> sprintService.start(callerId, sprintId))
                .isInstanceOf(ActiveSprintAlreadyExistsException.class);
        verify(eventPublisher, never()).publishEvent(any(SprintStartedEvent.class));
    }

    @Test
    void completeDoesNotPublishEventWhenSprintIsNotActive() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14));
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> sprintService.complete(callerId, sprintId))
                .isInstanceOf(IllegalSprintStateTransitionException.class);
        verify(eventPublisher, never()).publishEvent(any(SprintCompletedEvent.class));
    }

    @Test
    void completeBlockedWhenSprintIsNotActive() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14));
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> sprintService.complete(callerId, sprintId))
                .isInstanceOf(IllegalSprintStateTransitionException.class);
        assertThat(sprint.getState()).isEqualTo(SprintState.PLANNED);
        verify(issueService, never()).moveUnfinishedIssuesToBacklog(any(), any(), any());
    }

    @Test
    void completeRejectsCallerWhoIsNotOwnerOrAdmin() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14));
        sprint.setState(SprintState.ACTIVE);
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> sprintService.complete(callerId, sprintId))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(issueService, never()).moveUnfinishedIssuesToBacklog(any(), any(), any());
    }
}
