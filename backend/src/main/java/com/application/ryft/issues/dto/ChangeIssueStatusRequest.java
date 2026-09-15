package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.IssueStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeIssueStatusRequest(
        @NotNull IssueStatus status
) {
}
