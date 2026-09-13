package com.application.ryft.issues.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Full replace, not a partial patch: a comment has exactly one editable field. */
public record UpdateCommentRequest(
        @NotBlank @Size(max = 10000) String body
) {
}
