package com.application.ryft.search.exception;

/** Thrown when a Viewer tries to create a project-shared saved filter — see {@code SavedFilterServiceImpl}. */
public class InsufficientProjectRoleException extends RuntimeException {

    public InsufficientProjectRoleException() {
        super("Viewers may not share a saved filter with the project");
    }
}
