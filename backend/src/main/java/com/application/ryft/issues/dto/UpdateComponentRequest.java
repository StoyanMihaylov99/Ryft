package com.application.ryft.issues.dto;

import jakarta.validation.constraints.Size;

/** Partial update: a null/blank name is left unchanged (same convention as {@link UpdateIssueRequest}). */
public record UpdateComponentRequest(
        @Size(max = 100) String name
) {
}
