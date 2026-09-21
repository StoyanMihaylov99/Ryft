package com.application.ryft.common.event;

import java.util.UUID;

public record SprintCompletedEvent(
        UUID sprintId,
        UUID projectId,
        String projectKey,
        UUID actorId,
        String sprintName
) {
}
