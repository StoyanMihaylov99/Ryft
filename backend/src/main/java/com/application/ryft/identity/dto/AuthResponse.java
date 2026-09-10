package com.application.ryft.identity.dto;

public record AuthResponse(
        String accessToken,
        long expiresInSeconds,
        UserDTO user
) {
}
