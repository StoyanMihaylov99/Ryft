package com.application.ryft.seed.data;

/** {@code color} must be a 6-digit hex code, same constraint as {@code CreateLabelRequest.color}. */
public record SeedLabel(
        String name,
        String color
) {
}
