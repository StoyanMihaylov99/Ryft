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
        String actorDisplayName,
        Instant timestamp,
        Map<String, Object> payload
) {

    /**
     * {@code actorDisplayName} is resolved at read time (via {@code UserService}), not persisted on
     * {@link ActivityEvent} — same "denormalize for the response, don't store" pattern
     * {@code CommentResponse.authorDisplayName}/{@code NotificationResponse.actorDisplayName} already
     * use. {@code null} if the actor no longer resolves to a user (same convention as those two).
     */
    public static ActivityEventResponse from(ActivityEvent event, String actorDisplayName) {
        return new ActivityEventResponse(event.id(), event.projectId(), event.issueId(), event.eventType(),
                event.actorId(), actorDisplayName, event.timestamp(), event.payload());
    }
}
