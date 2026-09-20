package com.application.ryft.workflow.exception;

/**
 * This module's own translated copy of {@code projects.exception.InsufficientProjectRoleException} —
 * same rationale as {@code ProjectNotFoundException}'s javadoc. Owner/Admin-only gate for
 * {@code PATCH /projects/{projectKey}/workflow}, mirroring every other module's per-project settings
 * write (labels/components CRUD, sprint CRUD, {@code PATCH /projects/{key}}).
 */
public class InsufficientProjectRoleException extends RuntimeException {

    public InsufficientProjectRoleException() {
        super("Only the project Owner or an Admin can do this");
    }
}
