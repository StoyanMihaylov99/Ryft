package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.IssuePriority;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Partial update: a null field is left unchanged. There is currently no way to clear an assignee,
 * story points, or parent link through this endpoint (null means "don't touch it") — set a new value
 * instead. {@code parentId}, when provided, is subject to the same type-dependent parent-link rules as
 * on create (see IssueServiceImpl): a STORY/TASK/BUG's parentId must resolve to an EPIC, a SUBTASK's
 * must resolve to a STORY/TASK/BUG, and an EPIC's is rejected outright. There is no {@code type} field:
 * an issue's type is fixed at creation and never changes via PATCH.
 */
public record UpdateIssueRequest(
        @Size(max = 200) String title,
        @Size(max = 10000) String description,
        IssuePriority priority,
        UUID assigneeId,
        @PositiveOrZero Integer storyPoints,
        UUID parentId
) {
}
