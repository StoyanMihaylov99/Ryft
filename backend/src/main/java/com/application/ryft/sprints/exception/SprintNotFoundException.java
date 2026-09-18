package com.application.ryft.sprints.exception;

import java.util.UUID;

public class SprintNotFoundException extends RuntimeException {

    public SprintNotFoundException(UUID sprintId) {
        super("No sprint with id '" + sprintId + "'");
    }
}
