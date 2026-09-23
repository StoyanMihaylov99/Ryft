package com.application.ryft.search.exception;

/**
 * Translated copy of {@code issues.exception.ProjectNotFoundException} — this module has no
 * {@code @RestControllerAdvice} of its own that could catch the {@code issues} package's version, since
 * {@code @RestControllerAdvice(basePackages = "com.application.ryft.issues")} only applies to exceptions
 * thrown from controllers that live in that package, not to the exception type's own package. Same
 * pattern as {@code sprints.exception.ProjectNotFoundException} / {@code workflow.exception.ProjectNotFoundException}.
 */
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String key) {
        super("No project with key '" + key + "'");
    }
}
