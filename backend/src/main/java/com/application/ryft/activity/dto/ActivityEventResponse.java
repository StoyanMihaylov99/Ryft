package com.application.ryft.activity.dto;

import com.application.ryft.activity.entity.ActivityEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ActivityEventResponse(
        String id,
        UUID projectId,
        UUID issueId,
        String eventType,
        UUID actorId,
        Instant timestamp,
        Map<String, Object> payload
) {

    public static ActivityEventResponse from(ActivityEvent event) {
        return new ActivityEventResponse(event.id(), event.projectId(), event.issueId(), event.eventType(),
                event.actorId(), event.timestamp(), event.payload());
    }
}
