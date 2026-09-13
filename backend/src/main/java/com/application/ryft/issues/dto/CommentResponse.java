package com.application.ryft.issues.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID issueId,
        UUID authorId,
        String body,
        Instant createdAt,
        Instant updatedAt
) {
}
