package com.application.ryft.sprints.exception;

public class NoActiveSprintException extends RuntimeException {

    public NoActiveSprintException() {
        super("This project has no active sprint");
    }
}
