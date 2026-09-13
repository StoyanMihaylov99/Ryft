package com.application.ryft.identity.auth.dto;

import com.application.ryft.identity.user.dto.UserResponse;

public record AuthResponse(
        String accessToken,
        long expiresInSeconds,
        UserResponse user
) {
}
