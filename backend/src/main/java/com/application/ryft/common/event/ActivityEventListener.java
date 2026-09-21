package com.application.ryft.common.event;

import com.application.ryft.activity.service.ActivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes every meaningful state change to the activity log, off the request thread and only after the
 * triggering transaction has actually committed ({@link TransactionPhase#AFTER_COMMIT}) — logging an
 * event for a write that then rolled back would be a lie. Each method wraps its call to
 * {@link ActivityService} in its own try/catch: a failure here (e.g. Mongo temporarily unreachable)
 * must never propagate back into Spring's event-publishing machinery and turn an already-committed,
 * already-200-OK'd business operation into something the caller sees as a failure.
 */
@Component
public class ActivityEventListener {

    private static final Logger log = LoggerFactory.getLogger(ActivityEventListener.class);

    private final ActivityService activityService;

    public ActivityEventListener(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueCreated(IssueCreatedEvent event) {
        try {
            activityService.recordIssueCreated(event.projectId(), event.projectKey(), event.issueId(),
                    event.actorId(), event.title(), event.issueType());
        } catch (Exception e) {
            log.error("Failed to record activity for issue {} created", event.issueKey(), e);
        }
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueStatusChanged(IssueStatusChangedEvent event) {
        try {
            activityService.recordIssueStatusChanged(event.projectId(), event.projectKey(), event.issueId(),
                    event.actorId(), event.fromStatus(), event.toStatus());
        } catch (Exception e) {
            log.error("Failed to record activity for issue {} status change", event.issueKey(), e);
        }
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueAssigneeChanged(IssueAssigneeChangedEvent event) {
        try {
            activityService.recordIssueAssigneeChanged(event.projectId(), event.projectKey(), event.issueId(),
                    event.actorId(), event.previousAssigneeId(), event.newAssigneeId());
        } catch (Exception e) {
            log.error("Failed to record activity for issue {} assignee change", event.issueKey(), e);
        }
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentAdded(CommentAddedEvent event) {
        try {
            activityService.recordCommentAdded(event.projectId(), event.projectKey(), event.issueId(),
                    event.actorId(), event.commentId(), event.commentBody());
        } catch (Exception e) {
            log.error("Failed to record activity for comment {} added on issue {}", event.commentId(),
                    event.issueKey(), e);
        }
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentUpdated(CommentUpdatedEvent event) {
        try {
            activityService.recordCommentUpdated(event.projectId(), event.projectKey(), event.issueId(),
                    event.actorId(), event.commentId(), event.commentBody());
        } catch (Exception e) {
            log.error("Failed to record activity for comment {} updated on issue {}", event.commentId(),
                    event.issueKey(), e);
        }
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentDeleted(CommentDeletedEvent event) {
        try {
            activityService.recordCommentDeleted(event.projectId(), event.projectKey(), event.issueId(),
                    event.actorId(), event.commentId());
        } catch (Exception e) {
            log.error("Failed to record activity for comment {} deleted on issue {}", event.commentId(),
                    event.issueKey(), e);
        }
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSprintStarted(SprintStartedEvent event) {
        try {
            activityService.recordSprintStarted(event.projectId(), event.projectKey(), event.sprintId(),
                    event.actorId(), event.sprintName());
        } catch (Exception e) {
            log.error("Failed to record activity for sprint {} started", event.sprintId(), e);
        }
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSprintCompleted(SprintCompletedEvent event) {
        try {
            activityService.recordSprintCompleted(event.projectId(), event.projectKey(), event.sprintId(),
                    event.actorId(), event.sprintName());
        } catch (Exception e) {
            log.error("Failed to record activity for sprint {} completed", event.sprintId(), e);
        }
    }
}
