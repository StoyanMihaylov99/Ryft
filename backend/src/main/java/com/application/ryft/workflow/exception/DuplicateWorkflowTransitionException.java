package com.application.ryft.workflow.exception;

import java.util.UUID;

public class DuplicateWorkflowTransitionException extends RuntimeException {

    public DuplicateWorkflowTransitionException(UUID fromStatusId, UUID toStatusId) {
        super("A transition from " + fromStatusId + " to " + toStatusId + " already exists in this scheme");
    }
}
