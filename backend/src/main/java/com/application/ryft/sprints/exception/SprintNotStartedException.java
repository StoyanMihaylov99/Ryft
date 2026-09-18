package com.application.ryft.sprints.exception;

public class SprintNotStartedException extends RuntimeException {

    public SprintNotStartedException() {
        super("A planned sprint has no burndown data yet — start it first");
    }
}
