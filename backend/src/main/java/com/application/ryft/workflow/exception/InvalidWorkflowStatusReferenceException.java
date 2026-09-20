package com.application.ryft.workflow.exception;

import java.util.UUID;

public class InvalidWorkflowStatusReferenceException extends RuntimeException {

    public InvalidWorkflowStatusReferenceException(UUID statusId) {
        super("Status " + statusId + " does not resolve to a status already in this scheme");
    }
}
