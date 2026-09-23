package com.application.ryft.sprints.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.service.IssueService;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.sprints.dto.VelocityResponse;
import com.application.ryft.sprints.dto.VelocitySprintPoint;
import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.entity.SprintState;
import com.application.ryft.sprints.repository.SprintRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VelocityServiceImplTest {

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private SprintsProjectAccess projectAccess;

    @Mock
    private IssueService issueService;

    private VelocityServiceImpl velocityService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);

    @BeforeEach
    void setUp() {
        velocityService = new VelocityServiceImpl(sprintRepository, projectAccess, issueService);
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
    }

    private Sprint completedSprint(String name, Integer committedPoints, Instant completedAt) {
        Sprint sprint = new Sprint(projectId, name, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14));
        // Never persisted (repository is mocked), so @UuidGenerator never fires — assign an id directly,
        // same pattern as IssueServiceTest, so each sprint is distinguishable when stubbing listForSprint.
        ReflectionTestUtils.setField(sprint, "id", UUID.randomUUID());
        sprint.setState(SprintState.COMPLETED);
        sprint.setCommittedPoints(committedPoints);
        sprint.setCompletedAt(completedAt);
        return sprint;
    }

    private IssueResponse issue(Integer storyPoints) {
        return new IssueResponse(UUID.randomUUID(), projectId, "TRK-1", IssueType.TASK, "Title", null,
                IssueStatus.DONE, IssuePriority.MEDIUM, null, callerId, null, storyPoints, null, List.of(),
                List.of(), Instant.now(), Instant.now(), Instant.now());
    }

    @Test
    void noCompletedSprintsReturnsEmptyList() {
        when(sprintRepository.findByProjectIdAndStateOrderByCompletedAtDesc(
                projectId, SprintState.COMPLETED, PageRequest.of(0, 5)))
                .thenReturn(List.of());

        VelocityResponse result = velocityService.getVelocity(callerId, "TRK", 5);

        assertThat(result.projectId()).isEqualTo(projectId);
        assertThat(result.projectKey()).isEqualTo("TRK");
        assertThat(result.sprints()).isEmpty();
    }

    @Test
    void fewerCompletedSprintsThanLimitReturnsAllOfThemChronologically() {
        Instant firstCompletedAt = Instant.parse("2026-01-15T00:00:00Z");
        Instant secondCompletedAt = Instant.parse("2026-01-29T00:00:00Z");
        Sprint first = completedSprint("Sprint 1", 10, firstCompletedAt);
        Sprint second = completedSprint("Sprint 2", 12, secondCompletedAt);
        // Repository returns most-recent-first.
        when(sprintRepository.findByProjectIdAndStateOrderByCompletedAtDesc(
                projectId, SprintState.COMPLETED, PageRequest.of(0, 5)))
                .thenReturn(List.of(second, first));
        when(issueService.listForSprint(callerId, "TRK", first.getId())).thenReturn(List.of(issue(5)));
        when(issueService.listForSprint(callerId, "TRK", second.getId())).thenReturn(List.of(issue(7)));

        VelocityResponse result = velocityService.getVelocity(callerId, "TRK", 5);

        assertThat(result.sprints()).containsExactly(
                new VelocitySprintPoint(first.getId(), "Sprint 1", 10, 5, firstCompletedAt),
                new VelocitySprintPoint(second.getId(), "Sprint 2", 12, 7, secondCompletedAt));
    }

    @Test
    void moreCompletedSprintsThanLimitReturnsOnlyTheMostRecentNChronologically() {
        Sprint oldest = completedSprint("Sprint 1", 5, Instant.parse("2026-01-01T00:00:00Z"));
        Sprint middle = completedSprint("Sprint 2", 5, Instant.parse("2026-01-15T00:00:00Z"));
        Sprint newest = completedSprint("Sprint 3", 5, Instant.parse("2026-01-29T00:00:00Z"));
        // Only the trailing 2 are requested; the repository (given a limit-bounded Pageable) would only
        // return the 2 most recent, oldest excluded.
        when(sprintRepository.findByProjectIdAndStateOrderByCompletedAtDesc(
                projectId, SprintState.COMPLETED, PageRequest.of(0, 2)))
                .thenReturn(List.of(newest, middle));
        when(issueService.listForSprint(any(), any(), any())).thenReturn(List.of());

        VelocityResponse result = velocityService.getVelocity(callerId, "TRK", 2);

        assertThat(result.sprints()).extracting(VelocitySprintPoint::sprintName)
                .containsExactly("Sprint 2", "Sprint 3");
        assertThat(result.sprints()).doesNotContain(
                new VelocitySprintPoint(oldest.getId(), "Sprint 1", 5, 0, oldest.getCompletedAt()));
    }

    @Test
    void completedPointsOnlySumsIssuesStillAssignedToTheSprintAfterUnfinishedOnesMovedToBacklog() {
        Sprint sprint = completedSprint("Sprint 1", 20, Instant.parse("2026-01-15T00:00:00Z"));
        // Only the DONE issues remain assigned to a COMPLETED sprint — unfinished issues were moved back
        // to the backlog by SprintServiceImpl.complete, so they never appear in listForSprint's result.
        when(sprintRepository.findByProjectIdAndStateOrderByCompletedAtDesc(
                projectId, SprintState.COMPLETED, PageRequest.of(0, 5)))
                .thenReturn(List.of(sprint));
        when(issueService.listForSprint(callerId, "TRK", sprint.getId()))
                .thenReturn(List.of(issue(8), issue(5)));

        VelocityResponse result = velocityService.getVelocity(callerId, "TRK", 5);

        assertThat(result.sprints()).containsExactly(
                new VelocitySprintPoint(sprint.getId(), "Sprint 1", 20, 13, sprint.getCompletedAt()));
    }

    @Test
    void nullCommittedPointsDefaultsToZero() {
        Sprint sprint = completedSprint("Sprint 1", null, Instant.parse("2026-01-15T00:00:00Z"));
        when(sprintRepository.findByProjectIdAndStateOrderByCompletedAtDesc(
                any(), any(), any(Pageable.class)))
                .thenReturn(List.of(sprint));
        when(issueService.listForSprint(callerId, "TRK", sprint.getId())).thenReturn(List.of());

        VelocityResponse result = velocityService.getVelocity(callerId, "TRK", 5);

        assertThat(result.sprints()).containsExactly(
                new VelocitySprintPoint(sprint.getId(), "Sprint 1", 0, 0, sprint.getCompletedAt()));
    }
}
