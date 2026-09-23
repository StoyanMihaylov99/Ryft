package com.application.ryft.search.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code isShared}: {@code false} saves a private filter, visible only to the caller — any project member
 * (including Viewer) may do this. {@code true} shares it project-wide; {@code SavedFilterServiceImpl}
 * rejects that from a Viewer, the same write-vs-read-only line drawn for commenting/creating issues.
 */
public record CreateSavedFilterRequest(
        @NotBlank @Size(max = 100) String name,
        @Valid @NotNull IssueSearchRequest query,
        boolean isShared
) {
}
