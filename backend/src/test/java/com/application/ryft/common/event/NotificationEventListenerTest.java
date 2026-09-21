package com.application.ryft.common.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.application.ryft.notifications.service.NotificationService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationEventListener listener;

    private final UUID projectId = UUID.randomUUID();
    private final UUID issueId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID assigneeId = UUID.randomUUID();
    private final UUID commentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(notificationService);
    }

    @Test
    void onIssueCreatedNotifiesTheAssigneeWhenOneWasSetAtCreation() {
        listener.onIssueCreated(
                new IssueCreatedEvent(issueId, "TRK-1", projectId, "TRK", actorId, assigneeId, actorId, "Title",
                        "BUG"));

        verify(notificationService).notifyIssueAssigned(issueId, "TRK-1", projectId, "TRK", actorId, assigneeId);
    }

    @Test
    void onIssueCreatedDoesNothingWhenCreatedWithNoAssignee() {
        listener.onIssueCreated(
                new IssueCreatedEvent(issueId, "TRK-1", projectId, "TRK", actorId, null, actorId, "Title", "BUG"));

        verify(notificationService, never()).notifyIssueAssigned(any(), anyString(), any(), anyString(), any(),
                any());
    }

    @Test
    void onIssueCreatedDoesNotPropagateWhenNotificationServiceThrows() {
        doThrow(new RuntimeException("db down")).when(notificationService)
                .notifyIssueAssigned(any(), anyString(), any(), anyString(), any(), any());

        assertThatCode(() -> listener.onIssueCreated(new IssueCreatedEvent(issueId, "TRK-1", projectId, "TRK",
                actorId, assigneeId, actorId, "Title", "BUG"))).doesNotThrowAnyException();
    }

    @Test
    void onIssueStatusChangedDelegatesToNotifyStatusChanged() {
        UUID reporterId = UUID.randomUUID();

        listener.onIssueStatusChanged(new IssueStatusChangedEvent(issueId, "TRK-1", projectId, "TRK", actorId,
                assigneeId, reporterId, "To Do", "Done"));

        verify(notificationService).notifyStatusChanged(issueId, "TRK-1", projectId, "TRK", actorId, assigneeId,
                reporterId);
    }

    @Test
    void onIssueStatusChangedDoesNotPropagateWhenNotificationServiceThrows() {
        doThrow(new RuntimeException("db down")).when(notificationService)
                .notifyStatusChanged(any(), anyString(), any(), anyString(), any(), any(), any());

        assertThatCode(() -> listener.onIssueStatusChanged(new IssueStatusChangedEvent(issueId, "TRK-1", projectId,
                "TRK", actorId, assigneeId, actorId, "To Do", "Done"))).doesNotThrowAnyException();
    }

    @Test
    void onIssueAssigneeChangedDelegatesToNotifyIssueAssignedWithTheNewAssignee() {
        UUID previousAssignee = UUID.randomUUID();

        listener.onIssueAssigneeChanged(new IssueAssigneeChangedEvent(issueId, "TRK-1", projectId, "TRK", actorId,
                previousAssignee, assigneeId));

        verify(notificationService).notifyIssueAssigned(issueId, "TRK-1", projectId, "TRK", actorId, assigneeId);
    }

    @Test
    void onIssueAssigneeChangedDoesNotPropagateWhenNotificationServiceThrows() {
        doThrow(new RuntimeException("db down")).when(notificationService)
                .notifyIssueAssigned(any(), anyString(), any(), anyString(), any(), any());

        assertThatCode(() -> listener.onIssueAssigneeChanged(
                new IssueAssigneeChangedEvent(issueId, "TRK-1", projectId, "TRK", actorId, null, assigneeId)))
                .doesNotThrowAnyException();
    }

    @Test
    void onCommentAddedDelegatesToNotifyCommentAdded() {
        UUID reporterId = UUID.randomUUID();

        listener.onCommentAdded(new CommentAddedEvent(issueId, "TRK-1", projectId, "TRK", actorId, assigneeId,
                reporterId, commentId, "body"));

        verify(notificationService).notifyCommentAdded(issueId, "TRK-1", projectId, "TRK", actorId, assigneeId,
                reporterId, "body");
    }

    @Test
    void onCommentAddedDoesNotPropagateWhenNotificationServiceThrows() {
        doThrow(new RuntimeException("db down")).when(notificationService)
                .notifyCommentAdded(any(), anyString(), any(), anyString(), any(), any(), any(), anyString());

        assertThatCode(() -> listener.onCommentAdded(new CommentAddedEvent(issueId, "TRK-1", projectId, "TRK",
                actorId, assigneeId, actorId, commentId, "body"))).doesNotThrowAnyException();
    }
}
