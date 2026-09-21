package com.application.ryft.activity.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One row per meaningful state change (issue/comment/sprint), schema-flexible by design — see
 * DATA_MODEL.md's {@code activity} section. {@code issueId} is nullable: sprint-level events
 * (started/completed) have no issue to attach to. Modeled as an immutable record — Spring Data
 * MongoDB constructs it via its all-args constructor, same as any other persistence-constructor
 * mapping.
 */
@Document(collection = "activity_events")
public record ActivityEvent(
        @Id String id,
        UUID projectId,
        UUID issueId,
        String eventType,
        UUID actorId,
        Instant timestamp,
        Map<String, Object> payload
) {

    public static ActivityEvent of(UUID projectId, UUID issueId, String eventType, UUID actorId,
            Map<String, Object> payload) {
        return new ActivityEvent(null, projectId, issueId, eventType, actorId, Instant.now(), payload);
    }
}
