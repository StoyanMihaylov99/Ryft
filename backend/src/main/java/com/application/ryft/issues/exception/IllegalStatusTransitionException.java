package com.application.ryft.issues.exception;

import java.util.UUID;

/** 409 — the target status isn't a legal move from the issue's current status under its project's active workflow scheme. */
public class IllegalStatusTransitionException extends RuntimeException {

    public IllegalStatusTransitionException(UUID fromStatusId, UUID toStatusId) {
        super("Cannot move from status " + fromStatusId + " to " + toStatusId + " — no such transition is defined");
    }
}
