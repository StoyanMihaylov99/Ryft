package com.application.ryft.identity.auth.service;

public record IssuedAccessToken(String value, long expiresInSeconds) {
}
