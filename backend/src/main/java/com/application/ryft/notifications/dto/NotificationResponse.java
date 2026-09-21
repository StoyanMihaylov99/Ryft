package com.application.ryft.notifications.dto;

import com.application.ryft.notifications.entity.Notification;
import com.application.ryft.notifications.entity.NotificationType;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code actorDisplayName} is resolved at read/push time (via {@code UserService}), not persisted on
 * {@link Notification} — same "denormalize for the response, don't store" pattern {@code IssueResponse}
 * already uses for {@code statusName}. {@code null} if the actor no longer resolves to a user (same
 * convention as {@code CommentResponse.authorDisplayName}).
 */
public record NotificationResponse(
        UUID id,
        NotificationType type,
        UUID issueId,
        String issueKey,
        UUID actorId,
        String actorDisplayName,
        Instant readAt,
        Instant createdAt
) {

    public static NotificationResponse from(Notification notification, String actorDisplayName) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getIssueId(),
                notification.getIssueKey(), notification.getActorId(), actorDisplayName, notification.getReadAt(),
                notification.getCreatedAt());
    }
}
