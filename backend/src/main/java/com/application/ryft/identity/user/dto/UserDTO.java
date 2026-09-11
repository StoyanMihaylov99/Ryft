package com.application.ryft.identity.user.dto;

import java.util.UUID;

public record UserDTO(
        UUID id,
        String email,
        String displayName,
        String avatarUrl
) {
}
