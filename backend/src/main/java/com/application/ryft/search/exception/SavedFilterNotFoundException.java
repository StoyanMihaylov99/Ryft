package com.application.ryft.search.exception;

public class SavedFilterNotFoundException extends RuntimeException {

    public SavedFilterNotFoundException() {
        super("No saved filter with that id in this project");
    }
}
