package com.application.ryft.identity.exception;

public class WorkspaceAlreadySetUpException extends RuntimeException {

    public WorkspaceAlreadySetUpException() {
        super("This workspace has already been set up");
    }
}
