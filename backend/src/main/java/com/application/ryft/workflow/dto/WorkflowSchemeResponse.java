package com.application.ryft.workflow.dto;

import java.util.List;
import java.util.UUID;

public record WorkflowSchemeResponse(
        UUID id,
        UUID projectId,
        String name,
        List<WorkflowStatusResponse> statuses
) {
}
