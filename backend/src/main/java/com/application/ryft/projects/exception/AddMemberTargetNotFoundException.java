package com.application.ryft.projects.exception;

/** The target of an add-member call must already be a registered user; there is no email invite flow here. */
public class AddMemberTargetNotFoundException extends RuntimeException {

    public AddMemberTargetNotFoundException(String email) {
        super("No registered user with email '" + email + "'");
    }
}
