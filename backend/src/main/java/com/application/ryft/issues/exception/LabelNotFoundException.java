package com.application.ryft.issues.exception;

import java.util.UUID;

public class LabelNotFoundException extends RuntimeException {

    public LabelNotFoundException(UUID labelId) {
        super("No label with id '" + labelId + "' in this project");
    }
}
