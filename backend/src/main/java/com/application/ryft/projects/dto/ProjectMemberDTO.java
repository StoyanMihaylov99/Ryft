package com.application.ryft.projects.dto;

import com.application.ryft.projects.entity.ProjectRole;
import java.time.Instant;
import java.util.UUID;

public record ProjectMemberDTO(
        UUID userId,
        String email,
        String displayName,
        String avatarUrl,
        ProjectRole role,
        Instant addedAt
) {
}
