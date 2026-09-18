package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.Label;
import java.util.UUID;

public record LabelResponse(
        UUID id,
        UUID projectId,
        String name,
        String color
) {

    public static LabelResponse from(Label label) {
        return new LabelResponse(label.getId(), label.getProjectId(), label.getName(), label.getColor());
    }
}
