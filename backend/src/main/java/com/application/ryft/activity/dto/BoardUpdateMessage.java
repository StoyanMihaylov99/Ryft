package com.application.ryft.activity.dto;

import com.application.ryft.activity.entity.ActivityEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The live WebSocket push payload for {@code /topic/projects/{projectKey}/board} — not persisted.
 * Carries {@code projectKey} (unlike the persisted {@link ActivityEvent}, which only has
 * {@code projectId}) since the frontend subscribes per human-readable project key, not id.
 */
public record BoardUpdateMessage(
        String eventType,
        UUID projectId,
        String projectKey,
        UUID issueId,
        UUID actorId,
        Instant timestamp,
        Map<String, Object> payload
) {

    public static BoardUpdateMessage from(ActivityEvent event, String projectKey) {
        return new BoardUpdateMessage(event.eventType(), event.projectId(), projectKey, event.issueId(),
                event.actorId(), event.timestamp(), event.payload());
    }
}
