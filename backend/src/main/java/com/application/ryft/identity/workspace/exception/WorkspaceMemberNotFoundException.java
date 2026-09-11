package com.application.ryft.identity.workspace.exception;

public class WorkspaceMemberNotFoundException extends RuntimeException {

    public WorkspaceMemberNotFoundException() {
        super("That user is not a member of this workspace");
    }
}
