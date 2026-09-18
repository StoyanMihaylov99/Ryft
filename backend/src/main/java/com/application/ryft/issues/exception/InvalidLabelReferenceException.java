package com.application.ryft.issues.exception;

import java.util.UUID;

/** Thrown when a {@code labelIds} entry on create/update doesn't resolve to a label in the issue's own project. */
public class InvalidLabelReferenceException extends RuntimeException {

    public InvalidLabelReferenceException(UUID labelId) {
        super("Label " + labelId + " does not exist in this project");
    }
}
