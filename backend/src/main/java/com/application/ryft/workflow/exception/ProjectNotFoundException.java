package com.application.ryft.workflow.exception;

/**
 * This module's own translation of {@code projects.exception.ProjectNotFoundException} — see
 * {@code issues.exception.ProjectNotFoundException}'s javadoc for why every module that calls into
 * projects keeps its own copy rather than letting that exception propagate unchanged.
 */
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String key) {
        super("No project with key '" + key + "'");
    }
}
