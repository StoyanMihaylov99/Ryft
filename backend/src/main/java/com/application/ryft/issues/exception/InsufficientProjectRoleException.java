package com.application.ryft.issues.exception;

public class InsufficientProjectRoleException extends RuntimeException {

    public InsufficientProjectRoleException() {
        super("Only the project Owner or an Admin can do this");
    }
}
