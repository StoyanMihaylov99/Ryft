package com.application.ryft.identity.exception;

public class WorkspaceMemberNotFoundException extends RuntimeException {

    public WorkspaceMemberNotFoundException() {
        super("That user is not a member of this workspace");
    }
}
