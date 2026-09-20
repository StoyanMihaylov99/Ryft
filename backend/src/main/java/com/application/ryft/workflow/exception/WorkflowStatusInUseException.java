package com.application.ryft.workflow.exception;

public class WorkflowStatusInUseException extends RuntimeException {

    public WorkflowStatusInUseException(String statusName) {
        super("Status '" + statusName + "' is still assigned to at least one issue and cannot be deleted");
    }
}
