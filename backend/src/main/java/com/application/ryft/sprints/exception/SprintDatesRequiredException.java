package com.application.ryft.sprints.exception;

public class SprintDatesRequiredException extends RuntimeException {

    public SprintDatesRequiredException() {
        super("A sprint needs both a start date and an end date before it can be started");
    }
}
