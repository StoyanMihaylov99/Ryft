package com.application.ryft.projects.exception;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String key) {
        super("No project with key '" + key + "'");
    }
}
