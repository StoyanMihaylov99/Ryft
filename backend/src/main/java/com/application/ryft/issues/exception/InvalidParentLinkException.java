package com.application.ryft.issues.exception;

/**
 * Thrown for any violation of the {@code parentIssueId} rules — see {@code IssueServiceImpl}. Covers
 * both parent-link flavors that column now serves: STORY/TASK/BUG-to-EPIC (created in Phase 3's first
 * hierarchy step) and SUBTASK-to-STORY/TASK/BUG (added alongside it). One exception type rather than
 * two near-duplicates, since both are "this issue's parentId doesn't satisfy the rule for its type" —
 * the message passed in each throw site is what tells them apart for the caller.
 */
public class InvalidParentLinkException extends RuntimeException {

    public InvalidParentLinkException(String message) {
        super(message);
    }
}
