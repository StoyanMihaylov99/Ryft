package com.application.ryft.issues.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank @Size(max = 10000) String body
) {
}
