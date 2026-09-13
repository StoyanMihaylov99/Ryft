package com.application.ryft.issues.exception;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String key) {
        super("No project with key '" + key + "'");
    }
}
