package com.application.ryft.issues.exception;

/** Thrown for any violation of the STORY/TASK/BUG-to-EPIC parent-link rules — see IssueServiceImpl. */
public class InvalidEpicLinkException extends RuntimeException {

    public InvalidEpicLinkException(String message) {
        super(message);
    }
}
