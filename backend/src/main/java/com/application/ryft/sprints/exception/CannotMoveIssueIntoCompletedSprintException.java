package com.application.ryft.sprints.exception;

public class CannotMoveIssueIntoCompletedSprintException extends RuntimeException {

    public CannotMoveIssueIntoCompletedSprintException() {
        super("An issue cannot be moved into a completed sprint");
    }
}
