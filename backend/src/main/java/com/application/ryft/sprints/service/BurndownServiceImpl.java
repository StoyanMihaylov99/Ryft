package com.application.ryft.sprints.service;

import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.service.IssueService;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.sprints.dto.BurndownPoint;
import com.application.ryft.sprints.dto.BurndownResponse;
import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.entity.SprintState;
import com.application.ryft.sprints.exception.SprintNotStartedException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BurndownServiceImpl implements BurndownService {

    private final SprintLookupSupport sprintLookupSupport;
    private final SprintsProjectAccess projectAccess;
    private final IssueService issueService;

    public BurndownServiceImpl(SprintLookupSupport sprintLookupSupport, SprintsProjectAccess projectAccess,
            IssueService issueService) {
        this.sprintLookupSupport = sprintLookupSupport;
        this.projectAccess = projectAccess;
        this.issueService = issueService;
    }

    /**
     * No longer safe to be read-only. This was originally documented as safe, unlike
     * {@link SprintBoardServiceImpl#getBoard}, because its whole call chain —
     * {@link SprintLookupSupport#requireSprint} (a plain repository read),
     * {@link SprintsProjectAccess#requireMembershipByProjectId} (delegates to
     * {@code ProjectService.getById}, {@code @Transactional(readOnly = true)}), and
     * {@link IssueService#listForSprint} — never touched {@code WorkflowService}, the only source of the
     * lazy-seeding write documented in {@code ARCHITECTURE.md}'s read-only-transaction section, and each
     * link in the chain was itself read-only. That's no longer true: since Phase 4 gave {@code Issue} a
     * {@code workflowStatusId} column, {@link IssueService#listForSprint} routes its results through
     * {@code IssueLabelingService.toResponses}, the shared mapper that lazily backfills that column on
     * any pre-Phase-4 row it resolves (a real write) — so {@code listForSprint} is now plain
     * {@code @Transactional}, not read-only. Marking this method read-only would join that write into a
     * read-only transaction — Hibernate then sets FlushMode.MANUAL for the whole call, so the backfill is
     * staged but never flushed, silently leaving the column null forever. Same trap, same fix, as
     * {@code issues.service.BoardServiceImpl.getBoard} and {@code BacklogServiceImpl.listBacklog}.
     */
    @Override
    @Transactional
    public BurndownResponse getBurndown(UUID callerId, UUID sprintId) {
        Sprint sprint = sprintLookupSupport.requireSprint(sprintId);
        ProjectResponse project = projectAccess.requireMembershipByProjectId(callerId, sprint.getProjectId());
        if (sprint.getState() == SprintState.PLANNED) {
            throw new SprintNotStartedException();
        }

        List<IssueResponse> issues = issueService.listForSprint(callerId, project.key(), sprintId);
        int committedPoints = Objects.requireNonNullElse(sprint.getCommittedPoints(), 0);

        return new BurndownResponse(sprint.getId(), sprint.getName(), sprint.getStartDate(), sprint.getEndDate(),
                committedPoints, idealBurndown(sprint, committedPoints), actualBurndown(sprint, committedPoints, issues));
    }

    private List<BurndownPoint> idealBurndown(Sprint sprint, int committedPoints) {
        LocalDate startDate = sprint.getStartDate();
        LocalDate endDate = sprint.getEndDate();
        int totalDays = (int) ChronoUnit.DAYS.between(startDate, endDate);

        return startDate.datesUntil(endDate.plusDays(1))
                .map(date -> {
                    int dayIndex = (int) ChronoUnit.DAYS.between(startDate, date);
                    int remaining = totalDays == 0
                            ? 0
                            : (int) Math.round(committedPoints * (1.0 - (double) dayIndex / totalDays));
                    return new BurndownPoint(date, remaining);
                })
                .toList();
    }

    /**
     * Accepted v1 limitation: an issue moved out of the sprint (back to the backlog) before this
     * endpoint is called drops out of {@link IssueService#listForSprint}'s results entirely, so it
     * retroactively disappears from every day's historical sum too — there's no per-day activity/event
     * log yet (that's Phase 5 scope) to reconstruct sprint membership as it stood on a past date. Not a
     * bug to fix now, just a known trade-off of computing the actual line from current sprint
     * membership rather than a historical snapshot.
     */
    private List<BurndownPoint> actualBurndown(Sprint sprint, int committedPoints, List<IssueResponse> issues) {
        LocalDate startDate = sprint.getStartDate();
        // Must use the same clock as resolvedPointsOnOrBefore's UTC-based resolvedAt bucketing below —
        // comparing a host-default-zone "today" against a UTC-bucketed resolution date would silently
        // misclassify issues resolved near midnight on hosts with a negative UTC offset.
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate lastDay = sprint.getEndDate().isBefore(today) ? sprint.getEndDate() : today;
        if (lastDay.isBefore(startDate)) {
            return List.of();
        }

        return startDate.datesUntil(lastDay.plusDays(1))
                .map(date -> new BurndownPoint(date, committedPoints - resolvedPointsOnOrBefore(issues, date)))
                .toList();
    }

    private int resolvedPointsOnOrBefore(List<IssueResponse> issues, LocalDate date) {
        return issues.stream()
                .filter(issue -> issue.resolvedAt() != null)
                .filter(issue -> !issue.resolvedAt().atZone(ZoneOffset.UTC).toLocalDate().isAfter(date))
                .mapToInt(issue -> Objects.requireNonNullElse(issue.storyPoints(), 0))
                .sum();
    }
}
