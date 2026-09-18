package com.application.ryft.issues.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code color} must be a 6-digit hex code, e.g. {@code #4287F5}; name is unique per project (case-sensitive). */
public record CreateLabelRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a hex code like #RRGGBB") String color
) {
}
