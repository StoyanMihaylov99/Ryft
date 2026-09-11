package com.application.ryft.identity.dto;

import com.application.ryft.identity.repository.entity.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberDTO(
        UUID userId,
        String email,
        String displayName,
        String avatarUrl,
        WorkspaceRole role,
        Instant joinedAt
) {
}
