package com.application.ryft.identity.auth.service;

import com.application.ryft.identity.user.entity.User;

public record RefreshTokenRotationResult(User user, IssuedRefreshToken refreshToken) {
}
