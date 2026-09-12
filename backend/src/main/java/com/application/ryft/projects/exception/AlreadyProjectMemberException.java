package com.application.ryft.projects.exception;

public class AlreadyProjectMemberException extends RuntimeException {

    public AlreadyProjectMemberException(String email) {
        super("'" + email + "' is already a member of this project");
    }
}
