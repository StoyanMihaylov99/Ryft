package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import java.time.Instant;
import java.util.UUID;

public record IssueResponse(
        UUID id,
        UUID projectId,
        String key,
        IssueType type,
        String title,
        String description,
        IssueStatus status,
        IssuePriority priority,
        UUID assigneeId,
        UUID reporterId,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
) {
}
