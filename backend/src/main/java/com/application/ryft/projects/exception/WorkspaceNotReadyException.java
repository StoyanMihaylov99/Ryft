package com.application.ryft.projects.exception;

/** Projects are scoped to "the" v1 workspace, which doesn't exist until {@code POST /workspace/setup}. */
public class WorkspaceNotReadyException extends RuntimeException {

    public WorkspaceNotReadyException() {
        super("The workspace has not been set up yet");
    }
}
