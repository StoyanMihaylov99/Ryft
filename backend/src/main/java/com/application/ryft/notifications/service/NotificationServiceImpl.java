package com.application.ryft.notifications.service;

import com.application.ryft.identity.user.dto.UserResponse;
import com.application.ryft.identity.user.service.UserService;
import com.application.ryft.notifications.dto.NotificationResponse;
import com.application.ryft.notifications.entity.Notification;
import com.application.ryft.notifications.entity.NotificationType;
import com.application.ryft.notifications.exception.NotificationNotFoundException;
import com.application.ryft.notifications.repository.NotificationRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationsProjectAccess notificationsProjectAccess;
    private final UserService userService;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
            SimpMessagingTemplate messagingTemplate, NotificationsProjectAccess notificationsProjectAccess,
            UserService userService) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.notificationsProjectAccess = notificationsProjectAccess;
        this.userService = userService;
    }

    @Override
    @Transactional
    public void notifyIssueAssigned(UUID issueId, String issueKey, UUID projectId, String projectKey, UUID actorId,
            UUID assigneeId) {
        if (assigneeId.equals(actorId)) {
            return;
        }
        String actorDisplayName = resolveDisplayName(actorId);
        pushAndPersist(assigneeId, NotificationType.ASSIGNED, issueId, issueKey, actorId, actorDisplayName);
    }

    @Override
    @Transactional
    public void notifyStatusChanged(UUID issueId, String issueKey, UUID projectId, String projectKey, UUID actorId,
            UUID assigneeId, UUID reporterId) {
        String actorDisplayName = resolveDisplayName(actorId);
        for (UUID recipient : involvedPartiesExcludingActor(actorId, assigneeId, reporterId)) {
            pushAndPersist(recipient, NotificationType.STATUS_CHANGED, issueId, issueKey, actorId, actorDisplayName);
        }
    }

    @Override
    @Transactional
    public void notifyCommentAdded(UUID issueId, String issueKey, UUID projectId, String projectKey, UUID actorId,
            UUID assigneeId, UUID reporterId, String commentBody) {
        String actorDisplayName = resolveDisplayName(actorId);
        for (UUID recipient : involvedPartiesExcludingActor(actorId, assigneeId, reporterId)) {
            pushAndPersist(recipient, NotificationType.COMMENT, issueId, issueKey, actorId, actorDisplayName);
        }
        notifyMentionedUsers(issueId, issueKey, projectKey, actorId, actorDisplayName, commentBody);
    }

    /**
     * Deliberately does not call {@link NotificationsProjectAccess} at all when the comment body has no
     * "@" in it — the common case — to avoid a needless project-membership lookup per comment.
     */
    private void notifyMentionedUsers(UUID issueId, String issueKey, String projectKey, UUID actorId,
            String actorDisplayName, String commentBody) {
        Set<String> mentionedEmails = MentionParser.extractMentionedEmails(commentBody);
        if (mentionedEmails.isEmpty()) {
            return;
        }
        Map<String, UUID> memberIdsByEmail = notificationsProjectAccess.listMemberIdsByEmail(actorId, projectKey);
        for (String email : mentionedEmails) {
            UUID mentionedUserId = memberIdsByEmail.get(email);
            if (mentionedUserId != null && !mentionedUserId.equals(actorId)) {
                pushAndPersist(mentionedUserId, NotificationType.MENTION, issueId, issueKey, actorId,
                        actorDisplayName);
            }
        }
    }

    /** {@code null} if the actor no longer resolves to a user — same convention as CommentServiceImpl's authors. */
    private String resolveDisplayName(UUID userId) {
        UserResponse user = userService.findAllByIds(Set.of(userId)).get(userId);
        return user != null ? user.displayName() : null;
    }

    /** {@code Set<UUID>} so an assignee who is also the reporter is only notified once. */
    private Set<UUID> involvedPartiesExcludingActor(UUID actorId, UUID assigneeId, UUID reporterId) {
        Set<UUID> recipients = new HashSet<>();
        recipients.add(assigneeId);
        recipients.add(reporterId);
        recipients.remove(null);
        recipients.remove(actorId);
        return recipients;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(UUID userId) {
        List<Notification> notifications = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        Map<UUID, UserResponse> actorsById = fetchActors(notifications);
        return notifications.stream()
                .map(notification -> NotificationResponse.from(notification,
                        displayNameOf(actorsById, notification.getActorId())))
                .toList();
    }

    @Override
    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(NotificationNotFoundException::new);
        notification.markRead();
        return NotificationResponse.from(notification, resolveDisplayName(notification.getActorId()));
    }

    @Override
    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllReadForUser(userId, Instant.now());
    }

    /** One query for every distinct actor in the list, instead of one per notification. */
    private Map<UUID, UserResponse> fetchActors(List<Notification> notifications) {
        Set<UUID> actorIds = notifications.stream().map(Notification::getActorId).collect(Collectors.toSet());
        return userService.findAllByIds(actorIds);
    }

    private String displayNameOf(Map<UUID, UserResponse> actorsById, UUID actorId) {
        UserResponse actor = actorsById.get(actorId);
        return actor != null ? actor.displayName() : null;
    }

    private void pushAndPersist(UUID recipientId, NotificationType type, UUID issueId, String issueKey,
            UUID actorId, String actorDisplayName) {
        Notification saved = notificationRepository.save(
                new Notification(recipientId, type, issueId, issueKey, actorId));
        messagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/notifications",
                NotificationResponse.from(saved, actorDisplayName));
    }
}
