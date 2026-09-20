package com.application.ryft.sprints.exception;

public class SprintCompletedException extends RuntimeException {

    public SprintCompletedException() {
        super("A completed sprint can no longer be edited");
    }
}
