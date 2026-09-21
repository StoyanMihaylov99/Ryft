package com.application.ryft.common.event;

import java.util.UUID;

public record CommentAddedEvent(
        UUID issueId,
        String issueKey,
        UUID projectId,
        String projectKey,
        UUID actorId,
        UUID assigneeId,
        UUID reporterId,
        UUID commentId,
        String commentBody
) {
}
