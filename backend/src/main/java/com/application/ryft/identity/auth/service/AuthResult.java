package com.application.ryft.identity.auth.service;

import com.application.ryft.identity.user.dto.UserDTO;

public record AuthResult(
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken,
        UserDTO user
) {
}
