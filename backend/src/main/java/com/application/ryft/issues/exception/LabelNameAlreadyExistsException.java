package com.application.ryft.issues.exception;

/** Mirrors {@code ProjectKeyAlreadyExistsException} — same "duplicate name" 409 convention. */
public class LabelNameAlreadyExistsException extends RuntimeException {

    public LabelNameAlreadyExistsException(String name) {
        super("A label named '" + name + "' already exists in this project");
    }
}
