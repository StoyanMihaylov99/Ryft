package com.application.ryft.identity.service;

import com.application.ryft.identity.dto.UserDTO;

public record AuthResult(
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken,
        UserDTO user
) {
}
