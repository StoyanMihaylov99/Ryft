package com.application.ryft.common.event;

import java.util.UUID;

public record IssueAssigneeChangedEvent(
        UUID issueId,
        String issueKey,
        UUID projectId,
        String projectKey,
        UUID actorId,
        UUID previousAssigneeId,
        UUID newAssigneeId
) {
}
