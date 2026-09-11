package com.application.ryft.identity.exception;

public class CannotModifySelfRoleException extends RuntimeException {

    public CannotModifySelfRoleException() {
        super("You cannot change your own workspace role");
    }
}
