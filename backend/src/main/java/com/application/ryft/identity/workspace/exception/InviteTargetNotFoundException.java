package com.application.ryft.identity.workspace.exception;

/** v1 has no email/token invite flow — the invited address must already belong to a registered user. */
public class InviteTargetNotFoundException extends RuntimeException {

    public InviteTargetNotFoundException(String email) {
        super("No registered user with email '" + email + "'");
    }
}
