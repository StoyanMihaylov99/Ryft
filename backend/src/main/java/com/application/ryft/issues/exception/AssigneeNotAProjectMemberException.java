package com.application.ryft.issues.exception;

public class AssigneeNotAProjectMemberException extends RuntimeException {

    public AssigneeNotAProjectMemberException() {
        super("The assignee must be a member of this project");
    }
}
