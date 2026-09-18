package com.application.ryft.issues.entity;

/**
 * EPIC and SUBTASK both arrived with Phase 3's hierarchy work. SUBTASK issues are checklist-style
 * children of a STORY/TASK/BUG (own status, own key) — see {@code Issue.parentIssueId} and
 * {@code IssueServiceImpl}'s parent-link validation for the rules governing both link types.
 */
public enum IssueType {
    STORY,
    TASK,
    BUG,
    EPIC,
    SUBTASK
}
