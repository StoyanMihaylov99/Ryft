package com.application.ryft.workflow.dto;

import java.util.UUID;

public record WorkflowTransitionResponse(
        UUID id,
        UUID fromStatusId,
        String fromStatusName,
        UUID toStatusId,
        String toStatusName,
        String name
) {
}
