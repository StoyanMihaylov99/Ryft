package com.application.ryft.projects.exception;

/** v1 has exactly one Owner per project, set only at creation — never via add-member or role change. */
public class CannotAssignOwnerRoleException extends RuntimeException {

    public CannotAssignOwnerRoleException() {
        super("The Owner role cannot be assigned");
    }
}
