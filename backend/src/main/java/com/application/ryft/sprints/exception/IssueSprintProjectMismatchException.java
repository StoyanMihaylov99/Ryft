package com.application.ryft.sprints.exception;

public class IssueSprintProjectMismatchException extends RuntimeException {

    public IssueSprintProjectMismatchException() {
        super("The sprint does not belong to the issue's project");
    }
}
