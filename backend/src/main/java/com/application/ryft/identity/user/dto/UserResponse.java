package com.application.ryft.identity.user.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl
) {
}
