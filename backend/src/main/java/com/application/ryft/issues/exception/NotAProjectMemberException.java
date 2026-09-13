package com.application.ryft.issues.exception;

/** See {@link ProjectNotFoundException} for why this module keeps its own translated copy. */
public class NotAProjectMemberException extends RuntimeException {

    public NotAProjectMemberException() {
        super("You are not a member of this project");
    }
}
