package com.application.ryft.sprints.exception;

/** See {@link ProjectNotFoundException} for why this module keeps its own translated copy. */
public class IssueNotFoundException extends RuntimeException {

    public IssueNotFoundException(String key) {
        super("No issue with key '" + key + "'");
    }
}
