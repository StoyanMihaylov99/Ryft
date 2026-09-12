package com.application.ryft.projects.exception;

public class CannotModifySelfRoleException extends RuntimeException {

    public CannotModifySelfRoleException() {
        super("You cannot change your own project role");
    }
}
