package com.application.ryft.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.identity.user.dto.UserResponse;
import com.application.ryft.identity.user.service.UserService;
import com.application.ryft.notifications.dto.NotificationResponse;
import com.application.ryft.notifications.entity.Notification;
import com.application.ryft.notifications.entity.NotificationType;
import com.application.ryft.notifications.exception.NotificationNotFoundException;
import com.application.ryft.notifications.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private NotificationsProjectAccess notificationsProjectAccess;

    @Mock
    private UserService userService;

    private NotificationServiceImpl notificationService;

    private final UUID issueId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UserResponse actor = new UserResponse(actorId, "actor@example.com", "Actor Name", null);

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(notificationRepository, messagingTemplate,
                notificationsProjectAccess, userService);
        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userService.findAllByIds(any())).thenReturn(Map.of(actorId, actor));
    }

    @Test
    void notifyIssueAssignedIsANoOpWhenAssigneeIsTheActor() {
        notificationService.notifyIssueAssigned(issueId, "TRK-1", projectId, "TRK", actorId, actorId);

        verify(notificationRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void notifyIssueAssignedPersistsAndPushesToTheAssigneeWithIssueKeyAndActorDisplayName() {
        UUID assigneeId = UUID.randomUUID();

        notificationService.notifyIssueAssigned(issueId, "TRK-1", projectId, "TRK", actorId, assigneeId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(assigneeId);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.ASSIGNED);
        assertThat(captor.getValue().getIssueId()).isEqualTo(issueId);
        assertThat(captor.getValue().getIssueKey()).isEqualTo("TRK-1");
        assertThat(captor.getValue().getActorId()).isEqualTo(actorId);

        ArgumentCaptor<NotificationResponse> pushed = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(messagingTemplate).convertAndSendToUser(eq(assigneeId.toString()), eq("/queue/notifications"),
                pushed.capture());
        assertThat(pushed.getValue().issueKey()).isEqualTo("TRK-1");
        assertThat(pushed.getValue().actorDisplayName()).isEqualTo("Actor Name");
    }

    @Test
    void notifyStatusChangedNotifiesAssigneeAndReporterButNotTheActor() {
        UUID assigneeId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();

        notificationService.notifyStatusChanged(issueId, "TRK-1", projectId, "TRK", actorId, assigneeId, reporterId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(assigneeId, reporterId);
        assertThat(captor.getAllValues()).allMatch(n -> n.getType() == NotificationType.STATUS_CHANGED);
        assertThat(captor.getAllValues()).allMatch(n -> "TRK-1".equals(n.getIssueKey()));
    }

    @Test
    void notifyStatusChangedDeduplicatesWhenAssigneeAndReporterAreTheSamePerson() {
        UUID assigneeAndReporter = UUID.randomUUID();

        notificationService.notifyStatusChanged(issueId, "TRK-1", projectId, "TRK", actorId, assigneeAndReporter,
                assigneeAndReporter);

        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void notifyStatusChangedSkipsTheActorWhenTheyAreTheAssignee() {
        UUID reporterId = UUID.randomUUID();

        notificationService.notifyStatusChanged(issueId, "TRK-1", projectId, "TRK", actorId, actorId, reporterId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(reporterId);
    }

    @Test
    void notifyCommentAddedNeverCallsProjectServiceWhenBodyHasNoMention() {
        UUID assigneeId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();

        notificationService.notifyCommentAdded(issueId, "TRK-1", projectId, "TRK", actorId, assigneeId, reporterId,
                "No mentions here");

        verify(notificationsProjectAccess, never()).listMemberIdsByEmail(any(), any());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(n -> n.getType() == NotificationType.COMMENT);
        assertThat(captor.getAllValues()).allMatch(n -> "TRK-1".equals(n.getIssueKey()));
    }

    @Test
    void notifyCommentAddedNotifiesAMentionedProjectMemberWithIssueKeyAndActorDisplayName() {
        UUID mentionedUserId = UUID.randomUUID();
        when(notificationsProjectAccess.listMemberIdsByEmail(actorId, "TRK"))
                .thenReturn(Map.of("jane@example.com", mentionedUserId));

        notificationService.notifyCommentAdded(issueId, "TRK-1", projectId, "TRK", actorId, null, null,
                "cc @jane@example.com please");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(mentionedUserId);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.MENTION);
        assertThat(captor.getValue().getIssueKey()).isEqualTo("TRK-1");

        ArgumentCaptor<NotificationResponse> pushed = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(messagingTemplate).convertAndSendToUser(eq(mentionedUserId.toString()), eq("/queue/notifications"),
                pushed.capture());
        assertThat(pushed.getValue().actorDisplayName()).isEqualTo("Actor Name");
    }

    @Test
    void notifyCommentAddedDoesNotNotifyTheActorForSelfMention() {
        when(notificationsProjectAccess.listMemberIdsByEmail(actorId, "TRK"))
                .thenReturn(Map.of("actor@example.com", actorId));

        notificationService.notifyCommentAdded(issueId, "TRK-1", projectId, "TRK", actorId, null, null,
                "note to self @actor@example.com");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyCommentAddedGivesAnInvolvedAndMentionedUserBothNotificationTypes() {
        UUID assigneeAndMentioned = UUID.randomUUID();
        when(notificationsProjectAccess.listMemberIdsByEmail(actorId, "TRK"))
                .thenReturn(Map.of("assignee@example.com", assigneeAndMentioned));

        notificationService.notifyCommentAdded(issueId, "TRK-1", projectId, "TRK", actorId, assigneeAndMentioned,
                null, "@assignee@example.com please check");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.COMMENT, NotificationType.MENTION);
    }

    @Test
    void listForUserMapsEveryNotificationWithIssueKeyAndActorDisplayName() {
        UUID userId = UUID.randomUUID();
        Notification notification = new Notification(userId, NotificationType.COMMENT, issueId, "TRK-1", actorId);
        when(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(notification));

        List<NotificationResponse> result = notificationService.listForUser(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(NotificationType.COMMENT);
        assertThat(result.get(0).issueKey()).isEqualTo("TRK-1");
        assertThat(result.get(0).actorDisplayName()).isEqualTo("Actor Name");
    }

    @Test
    void listForUserLeavesActorDisplayNameNullWhenTheActorNoLongerResolves() {
        UUID userId = UUID.randomUUID();
        Notification notification = new Notification(userId, NotificationType.COMMENT, issueId, "TRK-1", actorId);
        when(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(notification));
        when(userService.findAllByIds(any())).thenReturn(Map.of());

        List<NotificationResponse> result = notificationService.listForUser(userId);

        assertThat(result.get(0).actorDisplayName()).isNull();
    }

    @Test
    void markReadThrowsWhenNotificationDoesNotBelongToCaller() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(userId, notificationId))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void markReadMarksTheNotificationReadAndResolvesIssueKeyAndActorDisplayName() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = new Notification(userId, NotificationType.COMMENT, issueId, "TRK-1", actorId);
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(notification));

        NotificationResponse result = notificationService.markRead(userId, notificationId);

        assertThat(result.readAt()).isNotNull();
        assertThat(result.issueKey()).isEqualTo("TRK-1");
        assertThat(result.actorDisplayName()).isEqualTo("Actor Name");
    }

    @Test
    void markAllReadDelegatesToTheBulkRepositoryMethod() {
        UUID userId = UUID.randomUUID();

        notificationService.markAllRead(userId);

        verify(notificationRepository).markAllReadForUser(eq(userId), any(Instant.class));
    }
}
