package com.application.ryft.issues.exception;

public class IssueNotFoundException extends RuntimeException {

    public IssueNotFoundException(String key) {
        super("No issue with key '" + key + "'");
    }
}
