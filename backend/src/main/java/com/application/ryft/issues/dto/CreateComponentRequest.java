package com.application.ryft.issues.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** name is unique per project (case-sensitive). */
public record CreateComponentRequest(
        @NotBlank @Size(max = 100) String name
) {
}
