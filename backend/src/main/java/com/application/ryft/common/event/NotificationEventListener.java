package com.application.ryft.common.event;

import com.application.ryft.notifications.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Determines recipients and delivers in-app notifications, same failure-isolation shape as
 * {@link ActivityEventListener} — see its javadoc. Deliberately only 4 methods: FEATURES.md's
 * notification triggers don't cover sprint start/complete or comment update/delete, so there's no
 * listener for {@link SprintStartedEvent}/{@link SprintCompletedEvent}/{@link CommentUpdatedEvent}/
 * {@link CommentDeletedEvent} here, even though {@link ActivityEventListener} does log all of those.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** An assignee set at creation time is notified the same way a later reassignment would notify them. */
    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueCreated(IssueCreatedEvent event) {
        try {
            if (event.assigneeId() != null) {
                notificationService.notifyIssueAssigned(event.issueId(), event.issueKey(), event.projectId(),
                        event.projectKey(), event.actorId(), event.assigneeId());
            }
        } catch (Exception e) {
            log.error("Failed to notify for issue {} created", event.issueKey(), e);
        }
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueStatusChanged(IssueStatusChangedEvent event) {
        try {
            notificationService.notifyStatusChanged(event.issueId(), event.issueKey(), event.projectId(),
                    event.projectKey(), event.actorId(), event.assigneeId(), event.reporterId());
        } catch (Exception e) {
            log.error("Failed to notify for issue {} status change", event.issueKey(), e);
        }
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueAssigneeChanged(IssueAssigneeChangedEvent event) {
        try {
            notificationService.notifyIssueAssigned(event.issueId(), event.issueKey(), event.projectId(),
                    event.projectKey(), event.actorId(), event.newAssigneeId());
        } catch (Exception e) {
            log.error("Failed to notify for issue {} assignee change", event.issueKey(), e);
        }
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentAdded(CommentAddedEvent event) {
        try {
            notificationService.notifyCommentAdded(event.issueId(), event.issueKey(), event.projectId(),
                    event.projectKey(), event.actorId(), event.assigneeId(), event.reporterId(),
                    event.commentBody());
        } catch (Exception e) {
            log.error("Failed to notify for comment {} added on issue {}", event.commentId(), event.issueKey(), e);
        }
    }
}
