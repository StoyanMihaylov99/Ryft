package com.application.ryft.seed.data;

/**
 * {@code startOffsetDays}/{@code endOffsetDays} are relative to the moment the seeder runs (negative =
 * in the past, positive = in the future) — resolved to a concrete {@code LocalDate} by
 * {@code DemoProjectInstaller}. This is the one place the seed data can't be fully honest about sprint
 * history: {@code Sprint.completedAt} (set by {@code SprintService#complete}) and every resolved issue's
 * {@code resolvedAt} are always "now" — {@code SprintService}/{@code IssueService} give no way to
 * backdate either through their public API, and reaching into their repositories to force one would
 * violate the module-boundary rule this seeder exists to respect. So a "COMPLETED" sprint's
 * {@code startDate}/{@code endDate} look properly historical (e.g. three weeks ago), but every issue in
 * it shows as resolved today. See {@code DemoProjectInstaller}'s class javadoc for the full explanation.
 *
 * <p><b>Each project's plan pins its most-recently-completed sprint's {@code endOffsetDays} to
 * {@code 0}</b> (today) rather than a genuinely past offset. {@code BurndownServiceImpl.getBurndown}
 * plots the actual-burndown line only up to {@code min(endDate, today)} — a completed sprint whose
 * {@code endDate} is already in the past (as a "real" historical sprint's would be) gets plotted entirely
 * *before* seed time, so every one of today's seed-time resolutions falls outside that range and the
 * line renders flat at {@code committedPoints} for its whole length. Pinning the most recent one's
 * {@code endDate} to today keeps it inside the plotted range, so its actual-burndown line shows a real,
 * visible drop instead. Any *older* completed sprint in the same project's plan is left with a genuinely
 * past {@code endOffsetDays} and does render flat — a plainly-documented limitation (see
 * {@code DemoProjectInstaller}'s class javadoc), not something worth compounding further pinned dates to
 * paper over.
 */
public record SeedSprint(
        String name,
        String goal,
        int startOffsetDays,
        int endOffsetDays,
        SeedSprintOutcome outcome
) {
}
