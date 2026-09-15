package com.application.ryft.workflow.dto;

import com.application.ryft.workflow.entity.StatusCategory;
import java.util.UUID;

public record WorkflowStatusResponse(
        UUID id,
        String name,
        StatusCategory category,
        int sortOrder
) {
}
