package com.application.ryft.workflow.exception;

public class NotAProjectMemberException extends RuntimeException {

    public NotAProjectMemberException() {
        super("You are not a member of this project");
    }
}
