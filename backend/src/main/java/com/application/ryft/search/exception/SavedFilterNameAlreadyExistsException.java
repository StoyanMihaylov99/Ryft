package com.application.ryft.search.exception;

/** Mirrors {@code issues.exception.LabelNameAlreadyExistsException} — same "duplicate name" 409 convention. */
public class SavedFilterNameAlreadyExistsException extends RuntimeException {

    public SavedFilterNameAlreadyExistsException(String name) {
        super("You already have a saved filter named '" + name + "' in this project");
    }
}
