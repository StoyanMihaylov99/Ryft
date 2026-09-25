package com.application.ryft.seed.data;

/**
 * What {@code DemoProjectInstaller} does to a sprint after every one of its planned issues has been
 * created and assigned: {@code COMPLETED} sprints are started, then completed (sweeping any
 * deliberately-left-unfinished issue back to the backlog, same as a real sprint close); {@code ACTIVE}
 * sprints are only started, left running so the board/burndown/sprint views have something live to show.
 */
public enum SeedSprintOutcome {
    COMPLETED,
    ACTIVE
}
