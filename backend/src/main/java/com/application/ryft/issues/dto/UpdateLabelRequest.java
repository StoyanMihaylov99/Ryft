package com.application.ryft.issues.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: a null field is left unchanged (same convention as {@link UpdateIssueRequest}).
 * There's no way to "clear" either field through this endpoint — a label always needs a name and a
 * color, so clearing one wouldn't be a legal state anyway.
 */
public record UpdateLabelRequest(
        @Size(max = 100) String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a hex code like #RRGGBB") String color
) {
}
