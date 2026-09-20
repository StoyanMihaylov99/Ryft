package com.application.ryft.issues.dto;

/**
 * {@code totalCount}/{@code doneCount} cover only the Epic's directly-linked STORY/TASK/BUG issues
 * (those whose {@code parentIssueId} equals the Epic's id) — a Subtask of one of those issues is a
 * separate hierarchy level and is deliberately not rolled up into its grandparent Epic's progress, per
 * FEATURES.md's "done issues / total issues under it" (read as the Epic's direct children, not a
 * 3-level rollup). {@code percentDone} is {@code 0} when {@code totalCount} is {@code 0} rather than
 * dividing by zero.
 */
public record EpicProgressResponse(int totalCount, int doneCount, double percentDone) {

    public static EpicProgressResponse of(long totalCount, long doneCount) {
        double percentDone = totalCount == 0 ? 0.0 : (doneCount * 100.0) / totalCount;
        return new EpicProgressResponse((int) totalCount, (int) doneCount, percentDone);
    }
}
