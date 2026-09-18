package com.application.ryft.sprints.exception;

public class InvalidSprintDateRangeException extends RuntimeException {

    public InvalidSprintDateRangeException() {
        super("A sprint's end date must be after its start date");
    }
}
