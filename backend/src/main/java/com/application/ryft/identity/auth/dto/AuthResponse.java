package com.application.ryft.identity.auth.dto;

import com.application.ryft.identity.user.dto.UserDTO;

public record AuthResponse(
        String accessToken,
        long expiresInSeconds,
        UserDTO user
) {
}
