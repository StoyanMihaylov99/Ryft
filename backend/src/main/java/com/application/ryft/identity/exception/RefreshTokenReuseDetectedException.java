package com.application.ryft.identity.exception;

/**
 * A refresh token that was already rotated away from (revoked) has been presented again, outside
 * the short reuse grace period — a signal the token was stolen. The entire rotation family has
 * already been revoked by the time this is thrown, forcing the legitimate user to re-login too.
 */
public class RefreshTokenReuseDetectedException extends RuntimeException {

    public RefreshTokenReuseDetectedException() {
        super("Refresh token reuse detected; session revoked");
    }
}
