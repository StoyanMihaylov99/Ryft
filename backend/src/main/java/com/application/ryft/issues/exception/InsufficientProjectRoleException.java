package com.application.ryft.issues.exception;

/**
 * Thrown both when the caller isn't Owner/Admin for an Owner/Admin-only action, and when a Member tries
 * to edit an issue they're not the assignee or reporter of (see {@code IssueServiceImpl.update}) — still
 * a flat 403 to the frontend either way, and nothing requires the client to distinguish the two causes.
 */
public class InsufficientProjectRoleException extends RuntimeException {

    public InsufficientProjectRoleException() {
        super("Only the project Owner or an Admin — or, for editing, an involved Member — can do this");
    }
}
