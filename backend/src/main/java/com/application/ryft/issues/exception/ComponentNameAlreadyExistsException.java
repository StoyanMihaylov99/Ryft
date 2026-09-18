package com.application.ryft.issues.exception;

/** Mirrors {@code ProjectKeyAlreadyExistsException} — same "duplicate name" 409 convention. */
public class ComponentNameAlreadyExistsException extends RuntimeException {

    public ComponentNameAlreadyExistsException(String name) {
        super("A component named '" + name + "' already exists in this project");
    }
}
