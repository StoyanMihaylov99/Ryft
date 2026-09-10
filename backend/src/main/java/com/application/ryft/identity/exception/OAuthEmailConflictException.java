package com.application.ryft.identity.exception;

/**
 * An OAuth2 provider reported an unverified email address that matches an existing account.
 * Rejected rather than merged: silently linking on an unverified email would let an attacker take
 * over a victim's account by registering an OAuth app with the victim's (unverified) email first.
 */
public class OAuthEmailConflictException extends RuntimeException {

    public OAuthEmailConflictException(String email) {
        super("Unverified email '" + email + "' matches an existing account");
    }
}
