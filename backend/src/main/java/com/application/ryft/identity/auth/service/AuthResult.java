package com.application.ryft.identity.auth.service;

import com.application.ryft.identity.user.dto.UserResponse;

public record AuthResult(
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken,
        UserResponse user
) {
}
