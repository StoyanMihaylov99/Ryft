package com.application.ryft.identity.exception;

public class AlreadyWorkspaceMemberException extends RuntimeException {

    public AlreadyWorkspaceMemberException(String email) {
        super("'" + email + "' is already a member of this workspace");
    }
}
