package com.application.ryft.identity.auth.exception;

/** Refresh token is missing, malformed, unknown, or expired. Not a theft signal — see
 * {@link RefreshTokenReuseDetectedException} for that. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
