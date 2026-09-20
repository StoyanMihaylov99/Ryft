package com.application.ryft.issues.dto;

/**
 * Both fields are nullable: an issue moved to the very top of the backlog has no
 * {@code beforeIssueKey}; moved to the very bottom, no {@code afterIssueKey}.
 */
public record ReorderBacklogIssueRequest(String beforeIssueKey, String afterIssueKey) {
}
