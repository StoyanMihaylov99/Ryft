package com.application.ryft.identity.exception;

/** v1 has exactly one Owner per workspace, set only at bootstrap — never via invite or role change. */
public class CannotAssignOwnerRoleException extends RuntimeException {

    public CannotAssignOwnerRoleException() {
        super("The Owner role cannot be assigned");
    }
}
