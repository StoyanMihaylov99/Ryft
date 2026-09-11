package com.application.ryft.identity.oauth.exception;

import com.application.ryft.identity.oauth.entity.OAuthProvider;

/** The OAuth2 provider returned no usable email address at all (rare — e.g. misconfigured scopes). */
public class OAuthMissingEmailException extends RuntimeException {

    public OAuthMissingEmailException(OAuthProvider provider) {
        super("No email address available from provider " + provider);
    }
}
