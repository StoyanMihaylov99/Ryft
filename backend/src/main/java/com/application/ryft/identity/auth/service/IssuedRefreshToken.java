package com.application.ryft.identity.auth.service;

import java.time.Instant;

public record IssuedRefreshToken(String rawValue, Instant expiresAt) {
}
