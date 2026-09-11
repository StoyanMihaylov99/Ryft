package com.application.ryft.identity.exception;

public class NotAWorkspaceMemberException extends RuntimeException {

    public NotAWorkspaceMemberException() {
        super("You are not a member of this workspace");
    }
}
