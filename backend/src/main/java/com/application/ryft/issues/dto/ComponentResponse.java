package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.Component;
import java.util.UUID;

public record ComponentResponse(
        UUID id,
        UUID projectId,
        String name
) {

    public static ComponentResponse from(Component component) {
        return new ComponentResponse(component.getId(), component.getProjectId(), component.getName());
    }
}
