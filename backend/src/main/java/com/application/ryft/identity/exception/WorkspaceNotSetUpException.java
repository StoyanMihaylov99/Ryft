package com.application.ryft.identity.exception;

/** v1's single workspace doesn't exist until someone completes {@code POST /workspace/setup}. */
public class WorkspaceNotSetUpException extends RuntimeException {

    public WorkspaceNotSetUpException() {
        super("This workspace has not been set up yet");
    }
}
