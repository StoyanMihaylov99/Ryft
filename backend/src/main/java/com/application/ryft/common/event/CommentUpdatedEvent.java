package com.application.ryft.common.event;

import java.util.UUID;

public record CommentUpdatedEvent(
        UUID issueId,
        String issueKey,
        UUID projectId,
        String projectKey,
        UUID actorId,
        UUID commentId,
        String commentBody
) {
}
