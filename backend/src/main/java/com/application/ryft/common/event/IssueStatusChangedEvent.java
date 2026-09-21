package com.application.ryft.common.event;

import java.util.UUID;

public record IssueStatusChangedEvent(
        UUID issueId,
        String issueKey,
        UUID projectId,
        String projectKey,
        UUID actorId,
        UUID assigneeId,
        UUID reporterId,
        String fromStatus,
        String toStatus
) {
}
