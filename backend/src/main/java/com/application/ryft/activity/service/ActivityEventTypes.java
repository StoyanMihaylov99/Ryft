package com.application.ryft.activity.service;

/** {@code event_type} discriminator values stored on every {@code ActivityEvent} — see DATA_MODEL.md. */
public final class ActivityEventTypes {

    public static final String ISSUE_CREATED = "issue.created";
    public static final String ISSUE_STATUS_CHANGED = "issue.status_changed";
    public static final String ISSUE_ASSIGNEE_CHANGED = "issue.assignee_changed";
    public static final String COMMENT_ADDED = "comment.added";
    public static final String COMMENT_UPDATED = "comment.updated";
    public static final String COMMENT_DELETED = "comment.deleted";
    public static final String SPRINT_STARTED = "sprint.started";
    public static final String SPRINT_COMPLETED = "sprint.completed";

    private ActivityEventTypes() {
    }
}
