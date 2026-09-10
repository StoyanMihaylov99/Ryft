package com.application.ryft.identity.service;

import com.application.ryft.identity.repository.entity.User;

public record RefreshTokenRotationResult(User user, IssuedRefreshToken refreshToken) {
}
