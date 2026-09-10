package com.application.ryft.identity.service;

import java.time.Instant;

public record IssuedRefreshToken(String rawValue, Instant expiresAt) {
}
