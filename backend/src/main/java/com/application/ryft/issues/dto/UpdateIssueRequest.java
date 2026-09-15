package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.IssuePriority;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Partial update: a null field is left unchanged. There is currently no way to clear an assignee
 * through this endpoint (null means "don't touch it") — reassign to someone else instead.
 */
public record UpdateIssueRequest(
        @Size(max = 200) String title,
        @Size(max = 10000) String description,
        IssuePriority priority,
        UUID assigneeId
) {
}
