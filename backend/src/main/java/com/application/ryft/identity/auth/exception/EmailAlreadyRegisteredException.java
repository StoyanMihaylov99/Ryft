package com.application.ryft.identity.auth.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("An account with email '" + email + "' already exists");
    }
}
