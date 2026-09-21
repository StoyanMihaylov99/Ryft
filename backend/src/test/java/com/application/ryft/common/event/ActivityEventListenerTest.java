package com.application.ryft.common.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.application.ryft.activity.service.ActivityService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Proves the failure-isolation design actually holds: a downstream {@link ActivityService} failure
 * must never propagate out of the listener, since these run {@code AFTER_COMMIT} — by the time they
 * fire, the triggering business operation has already succeeded and its HTTP response may have
 * already gone out.
 */
@ExtendWith(MockitoExtension.class)
class ActivityEventListenerTest {

    @Mock
    private ActivityService activityService;

    private ActivityEventListener listener;

    private final UUID projectId = UUID.randomUUID();
    private final UUID issueId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID sprintId = UUID.randomUUID();
    private final UUID commentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new ActivityEventListener(activityService);
    }

    @Test
    void onIssueCreatedDoesNotPropagateWhenActivityServiceThrows() {
        doThrow(new RuntimeException("mongo down")).when(activityService)
                .recordIssueCreated(any(), anyString(), any(), any(), anyString(), anyString());

        assertThatCode(() -> listener.onIssueCreated(
                new IssueCreatedEvent(issueId, "TRK-1", projectId, "TRK", actorId, null, actorId, "Title", "BUG")))
                .doesNotThrowAnyException();
        verify(activityService).recordIssueCreated(projectId, "TRK", issueId, actorId, "Title", "BUG");
    }

    @Test
    void onIssueStatusChangedDoesNotPropagateWhenActivityServiceThrows() {
        doThrow(new RuntimeException("mongo down")).when(activityService)
                .recordIssueStatusChanged(any(), anyString(), any(), any(), anyString(), anyString());

        assertThatCode(() -> listener.onIssueStatusChanged(new IssueStatusChangedEvent(issueId, "TRK-1", projectId,
                "TRK", actorId, null, actorId, "To Do", "Done"))).doesNotThrowAnyException();
    }

    @Test
    void onIssueAssigneeChangedDoesNotPropagateWhenActivityServiceThrows() {
        doThrow(new RuntimeException("mongo down")).when(activityService)
                .recordIssueAssigneeChanged(any(), anyString(), any(), any(), any(), any());

        assertThatCode(() -> listener.onIssueAssigneeChanged(new IssueAssigneeChangedEvent(issueId, "TRK-1",
                projectId, "TRK", actorId, null, actorId))).doesNotThrowAnyException();
    }

    @Test
    void onCommentAddedDoesNotPropagateWhenActivityServiceThrows() {
        doThrow(new RuntimeException("mongo down")).when(activityService)
                .recordCommentAdded(any(), anyString(), any(), any(), any(), anyString());

        assertThatCode(() -> listener.onCommentAdded(new CommentAddedEvent(issueId, "TRK-1", projectId, "TRK",
                actorId, null, actorId, commentId, "body"))).doesNotThrowAnyException();
    }

    @Test
    void onCommentUpdatedDoesNotPropagateWhenActivityServiceThrows() {
        doThrow(new RuntimeException("mongo down")).when(activityService)
                .recordCommentUpdated(any(), anyString(), any(), any(), any(), anyString());

        assertThatCode(() -> listener.onCommentUpdated(
                new CommentUpdatedEvent(issueId, "TRK-1", projectId, "TRK", actorId, commentId, "body")))
                .doesNotThrowAnyException();
    }

    @Test
    void onCommentDeletedDoesNotPropagateWhenActivityServiceThrows() {
        doThrow(new RuntimeException("mongo down")).when(activityService)
                .recordCommentDeleted(any(), anyString(), any(), any(), any());

        assertThatCode(() -> listener.onCommentDeleted(
                new CommentDeletedEvent(issueId, "TRK-1", projectId, "TRK", actorId, commentId)))
                .doesNotThrowAnyException();
    }

    @Test
    void onSprintStartedDoesNotPropagateWhenActivityServiceThrows() {
        doThrow(new RuntimeException("mongo down")).when(activityService)
                .recordSprintStarted(any(), anyString(), any(), any(), anyString());

        assertThatCode(() -> listener.onSprintStarted(
                new SprintStartedEvent(sprintId, projectId, "TRK", actorId, "Sprint 1")))
                .doesNotThrowAnyException();
    }

    @Test
    void onSprintCompletedDoesNotPropagateWhenActivityServiceThrows() {
        doThrow(new RuntimeException("mongo down")).when(activityService)
                .recordSprintCompleted(any(), anyString(), any(), any(), anyString());

        assertThatCode(() -> listener.onSprintCompleted(
                new SprintCompletedEvent(sprintId, projectId, "TRK", actorId, "Sprint 1")))
                .doesNotThrowAnyException();
    }
}
