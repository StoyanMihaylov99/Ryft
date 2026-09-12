package com.application.ryft.projects.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectDTO(
        UUID id,
        UUID workspaceId,
        String key,
        String name,
        String description,
        Instant createdAt,
        Instant archivedAt
) {
}
