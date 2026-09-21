package com.application.ryft.common.event;

import java.util.UUID;

public record SprintStartedEvent(
        UUID sprintId,
        UUID projectId,
        String projectKey,
        UUID actorId,
        String sprintName
) {
}
