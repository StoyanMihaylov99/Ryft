package com.application.ryft.sprints.exception;

public class InsufficientProjectRoleException extends RuntimeException {

    public InsufficientProjectRoleException() {
        super("Only the project Owner or an Admin can do this");
    }
}
