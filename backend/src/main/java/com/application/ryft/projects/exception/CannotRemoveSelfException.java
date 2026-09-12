package com.application.ryft.projects.exception;

public class CannotRemoveSelfException extends RuntimeException {

    public CannotRemoveSelfException() {
        super("You cannot remove yourself from the project");
    }
}
