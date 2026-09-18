package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code priority} defaults to {@code MEDIUM} when omitted. {@code parentId} links a STORY/TASK/BUG to
 * an EPIC in the same project — only valid for those types, and only when it points at an EPIC (see
 * IssueServiceImpl for the enforcement).
 */
public record CreateIssueRequest(
        @NotNull IssueType type,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description,
        IssuePriority priority,
        UUID assigneeId,
        @PositiveOrZero Integer storyPoints,
        UUID parentId
) {
}
