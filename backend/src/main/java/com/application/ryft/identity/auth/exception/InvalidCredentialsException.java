package com.application.ryft.identity.auth.exception;

/**
 * Thrown for both "unknown email" and "wrong password" — deliberately the same exception/message
 * for both cases so the API response can't be used to enumerate registered email addresses.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
