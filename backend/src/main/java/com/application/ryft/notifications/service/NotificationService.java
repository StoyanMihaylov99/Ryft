package com.application.ryft.notifications.service;

import com.application.ryft.notifications.dto.NotificationResponse;
import java.util.List;
import java.util.UUID;

public interface NotificationService {

    /** No-op if the assignee is the same person who made the change. */
    void notifyIssueAssigned(UUID issueId, String issueKey, UUID projectId, String projectKey, UUID actorId,
            UUID assigneeId);

    /** Notifies the assignee and reporter (deduplicated, excluding the actor). */
    void notifyStatusChanged(UUID issueId, String issueKey, UUID projectId, String projectKey, UUID actorId,
            UUID assigneeId, UUID reporterId);

    /**
     * Notifies the assignee and reporter (deduplicated, excluding the actor), then separately notifies
     * every project member whose email is {@code @}-mentioned in the comment body — a person who is
     * both an involved party and mentioned gets both notifications, deliberately not deduplicated across
     * the two types.
     */
    void notifyCommentAdded(UUID issueId, String issueKey, UUID projectId, String projectKey, UUID actorId,
            UUID assigneeId, UUID reporterId, String commentBody);

    List<NotificationResponse> listForUser(UUID userId);

    NotificationResponse markRead(UUID userId, UUID notificationId);

    void markAllRead(UUID userId);
}
