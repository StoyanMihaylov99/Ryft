package com.application.ryft.sprints.exception;

public class ActiveSprintAlreadyExistsException extends RuntimeException {

    public ActiveSprintAlreadyExistsException() {
        super("This project already has an active sprint; complete it before starting another");
    }
}
