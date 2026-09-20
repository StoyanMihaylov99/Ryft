package com.application.ryft.sprints.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.service.IssueService;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.sprints.dto.BurndownPoint;
import com.application.ryft.sprints.dto.BurndownResponse;
import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.entity.SprintState;
import com.application.ryft.sprints.exception.NotAProjectMemberException;
import com.application.ryft.sprints.exception.SprintNotStartedException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BurndownServiceImplTest {

    @Mock
    private SprintLookupSupport sprintLookupSupport;

    @Mock
    private SprintsProjectAccess projectAccess;

    @Mock
    private IssueService issueService;

    private BurndownServiceImpl burndownService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID sprintId = UUID.randomUUID();
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);

    @BeforeEach
    void setUp() {
        burndownService = new BurndownServiceImpl(sprintLookupSupport, projectAccess, issueService);
    }

    private Sprint activeSprint(LocalDate startDate, LocalDate endDate, int committedPoints) {
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, startDate, endDate);
        sprint.setState(SprintState.ACTIVE);
        sprint.setCommittedPoints(committedPoints);
        return sprint;
    }

    private IssueResponse issue(Integer storyPoints, Instant resolvedAt) {
        return new IssueResponse(UUID.randomUUID(), projectId, "TRK-1", IssueType.TASK, "Title", null,
                resolvedAt != null ? IssueStatus.DONE : IssueStatus.TODO, IssuePriority.MEDIUM, null, callerId, null,
                storyPoints, Instant.now(), Instant.now(), resolvedAt);
    }

    @Test
    void idealBurndownDecreasesLinearlyFromCommittedPointsToZero() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 11);
        Sprint sprint = activeSprint(start, end, 20);
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(issueService.listForSprint(callerId, "TRK", sprintId)).thenReturn(List.of());

        BurndownResponse result = burndownService.getBurndown(callerId, sprintId);

        List<BurndownPoint> ideal = result.idealBurndown();
        assertThat(ideal).hasSize(11);
        assertThat(ideal.get(0)).isEqualTo(new BurndownPoint(start, 20));
        assertThat(ideal.get(5)).isEqualTo(new BurndownPoint(LocalDate.of(2026, 1, 6), 10));
        assertThat(ideal.get(10)).isEqualTo(new BurndownPoint(end, 0));
    }

    @Test
    void actualBurndownBucketsResolvedStoryPointsByDayTreatingNullAsZeroAndIgnoringUnresolvedIssues() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 14);
        Sprint sprint = activeSprint(start, end, 20);
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);

        Instant resolvedJan3 = LocalDate.of(2026, 1, 3).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant resolvedJan10 = LocalDate.of(2026, 1, 10).atStartOfDay(ZoneOffset.UTC).toInstant();
        IssueResponse resolvedWithPoints = issue(5, resolvedJan3);
        IssueResponse resolvedWithNullPoints = issue(null, resolvedJan10);
        IssueResponse stillUnresolved = issue(8, null);
        when(issueService.listForSprint(callerId, "TRK", sprintId))
                .thenReturn(List.of(resolvedWithPoints, resolvedWithNullPoints, stillUnresolved));

        BurndownResponse result = burndownService.getBurndown(callerId, sprintId);

        List<BurndownPoint> actual = result.actualBurndown();
        assertThat(actual).hasSize(14);
        assertThat(pointOn(actual, LocalDate.of(2026, 1, 1)).remainingPoints()).isEqualTo(20);
        assertThat(pointOn(actual, LocalDate.of(2026, 1, 3)).remainingPoints()).isEqualTo(15);
        assertThat(pointOn(actual, LocalDate.of(2026, 1, 9)).remainingPoints()).isEqualTo(15);
        assertThat(pointOn(actual, LocalDate.of(2026, 1, 10)).remainingPoints()).isEqualTo(15);
        assertThat(pointOn(actual, LocalDate.of(2026, 1, 14)).remainingPoints()).isEqualTo(15);
    }

    private BurndownPoint pointOn(List<BurndownPoint> points, LocalDate date) {
        return points.stream().filter(p -> p.date().equals(date)).findFirst().orElseThrow();
    }

    @Test
    void plannedSprintThrows() {
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14));
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);

        assertThatThrownBy(() -> burndownService.getBurndown(callerId, sprintId))
                .isInstanceOf(SprintNotStartedException.class);
    }

    @Test
    void nonMemberCallerThrowsTranslatedMembershipException() {
        Sprint sprint = activeSprint(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14), 20);
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId))
                .thenThrow(new NotAProjectMemberException());

        assertThatThrownBy(() -> burndownService.getBurndown(callerId, sprintId))
                .isInstanceOf(NotAProjectMemberException.class);
    }

    @Test
    void sameDaySprintDoesNotDivideByZero() {
        LocalDate onlyDay = LocalDate.of(2026, 1, 1);
        Sprint sprint = activeSprint(onlyDay, onlyDay, 20);
        when(sprintLookupSupport.requireSprint(sprintId)).thenReturn(sprint);
        when(projectAccess.requireMembershipByProjectId(callerId, projectId)).thenReturn(project);
        when(issueService.listForSprint(callerId, "TRK", sprintId)).thenReturn(List.of());

        BurndownResponse result = burndownService.getBurndown(callerId, sprintId);

        assertThat(result.idealBurndown()).containsExactly(new BurndownPoint(onlyDay, 0));
        assertThat(result.actualBurndown()).hasSize(1);
        assertThat(result.actualBurndown().get(0).date()).isEqualTo(onlyDay);
    }
}
