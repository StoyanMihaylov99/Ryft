package com.application.ryft.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(min = 2, max = 10) @Pattern(regexp = "^[A-Z][A-Z0-9]*$",
                message = "must start with a letter and contain only uppercase letters and digits") String key,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description
) {
}
