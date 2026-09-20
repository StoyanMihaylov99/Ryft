package com.application.ryft.issues.exception;

import java.util.UUID;

/** Thrown when a {@code componentIds} entry on create/update doesn't resolve to a component in the issue's own project. */
public class InvalidComponentReferenceException extends RuntimeException {

    public InvalidComponentReferenceException(UUID componentId) {
        super("Component " + componentId + " does not exist in this project");
    }
}
