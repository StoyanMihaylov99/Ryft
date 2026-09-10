package com.application.ryft.identity.service;

public record IssuedAccessToken(String value, long expiresInSeconds) {
}
