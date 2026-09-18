package com.application.ryft.issues.exception;

import java.util.UUID;

public class ComponentNotFoundException extends RuntimeException {

    public ComponentNotFoundException(UUID componentId) {
        super("No component with id '" + componentId + "' in this project");
    }
}
