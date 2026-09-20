package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code priority} defaults to {@code MEDIUM} when omitted. */
public record CreateIssueRequest(
        @NotNull IssueType type,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description,
        IssuePriority priority,
        UUID assigneeId
) {
}
