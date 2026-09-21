package com.application.ryft.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.activity.dto.ActivityEventResponse;
import com.application.ryft.activity.dto.BoardUpdateMessage;
import com.application.ryft.activity.entity.ActivityEvent;
import com.application.ryft.activity.repository.ActivityEventRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class ActivityServiceImplTest {

    @Mock
    private ActivityEventRepository activityEventRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ActivityServiceImpl activityService;

    private final UUID projectId = UUID.randomUUID();
    private final UUID issueId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        activityService = new ActivityServiceImpl(activityEventRepository, messagingTemplate);
        lenient().when(activityEventRepository.save(any(ActivityEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void recordIssueCreatedStoresTitleAndIssueType() {
        activityService.recordIssueCreated(projectId, "TRK", issueId, actorId, "Fix login", "BUG");

        ActivityEvent saved = captureSaved();
        assertThat(saved.eventType()).isEqualTo(ActivityEventTypes.ISSUE_CREATED);
        assertThat(saved.projectId()).isEqualTo(projectId);
        assertThat(saved.issueId()).isEqualTo(issueId);
        assertThat(saved.actorId()).isEqualTo(actorId);
        assertThat(saved.payload()).containsEntry("title", "Fix login").containsEntry("issue_type", "BUG");
        verifyPushedToBoardTopic("TRK");
    }

    @Test
    void recordIssueStatusChangedStoresFromAndToStatus() {
        activityService.recordIssueStatusChanged(projectId, "TRK", issueId, actorId, "To Do", "Done");

        ActivityEvent saved = captureSaved();
        assertThat(saved.eventType()).isEqualTo(ActivityEventTypes.ISSUE_STATUS_CHANGED);
        assertThat(saved.payload()).containsEntry("from_status", "To Do").containsEntry("to_status", "Done");
    }

    @Test
    void recordIssueAssigneeChangedStoresPreviousAndNewAssignee() {
        UUID previousAssignee = UUID.randomUUID();
        UUID newAssignee = UUID.randomUUID();

        activityService.recordIssueAssigneeChanged(projectId, "TRK", issueId, actorId, previousAssignee,
                newAssignee);

        ActivityEvent saved = captureSaved();
        assertThat(saved.eventType()).isEqualTo(ActivityEventTypes.ISSUE_ASSIGNEE_CHANGED);
        assertThat(saved.payload()).containsEntry("previous_assignee_id", previousAssignee)
                .containsEntry("new_assignee_id", newAssignee);
    }

    @Test
    void recordIssueAssigneeChangedToleratesNoPreviousAssignee() {
        UUID newAssignee = UUID.randomUUID();

        activityService.recordIssueAssigneeChanged(projectId, "TRK", issueId, actorId, null, newAssignee);

        ActivityEvent saved = captureSaved();
        assertThat(saved.payload()).containsEntry("previous_assignee_id", null)
                .containsEntry("new_assignee_id", newAssignee);
    }

    @Test
    void recordCommentAddedStoresCommentIdAndExcerpt() {
        UUID commentId = UUID.randomUUID();

        activityService.recordCommentAdded(projectId, "TRK", issueId, actorId, commentId, "Looks good to me");

        ActivityEvent saved = captureSaved();
        assertThat(saved.eventType()).isEqualTo(ActivityEventTypes.COMMENT_ADDED);
        assertThat(saved.payload()).containsEntry("comment_id", commentId).containsEntry("excerpt",
                "Looks good to me");
    }

    @Test
    void recordCommentAddedTruncatesLongBodiesToA140CharacterExcerpt() {
        String longBody = "x".repeat(200);

        activityService.recordCommentAdded(projectId, "TRK", issueId, actorId, UUID.randomUUID(), longBody);

        ActivityEvent saved = captureSaved();
        assertThat(((String) saved.payload().get("excerpt"))).hasSize(140);
    }

    @Test
    void recordCommentUpdatedStoresCommentIdAndExcerpt() {
        UUID commentId = UUID.randomUUID();

        activityService.recordCommentUpdated(projectId, "TRK", issueId, actorId, commentId, "Edited body");

        ActivityEvent saved = captureSaved();
        assertThat(saved.eventType()).isEqualTo(ActivityEventTypes.COMMENT_UPDATED);
        assertThat(saved.payload()).containsEntry("comment_id", commentId).containsEntry("excerpt", "Edited body");
    }

    @Test
    void recordCommentDeletedStoresOnlyCommentId() {
        UUID commentId = UUID.randomUUID();

        activityService.recordCommentDeleted(projectId, "TRK", issueId, actorId, commentId);

        ActivityEvent saved = captureSaved();
        assertThat(saved.eventType()).isEqualTo(ActivityEventTypes.COMMENT_DELETED);
        assertThat(saved.payload()).containsEntry("comment_id", commentId);
        assertThat(saved.issueId()).isEqualTo(issueId);
    }

    @Test
    void recordSprintStartedHasNoIssueIdAndStoresSprintNameAndId() {
        UUID sprintId = UUID.randomUUID();

        activityService.recordSprintStarted(projectId, "TRK", sprintId, actorId, "Sprint 1");

        ActivityEvent saved = captureSaved();
        assertThat(saved.eventType()).isEqualTo(ActivityEventTypes.SPRINT_STARTED);
        assertThat(saved.issueId()).isNull();
        assertThat(saved.payload()).containsEntry("sprint_id", sprintId).containsEntry("sprint_name", "Sprint 1");
    }

    @Test
    void recordSprintCompletedHasNoIssueIdAndStoresSprintNameAndId() {
        UUID sprintId = UUID.randomUUID();

        activityService.recordSprintCompleted(projectId, "TRK", sprintId, actorId, "Sprint 1");

        ActivityEvent saved = captureSaved();
        assertThat(saved.eventType()).isEqualTo(ActivityEventTypes.SPRINT_COMPLETED);
        assertThat(saved.issueId()).isNull();
        assertThat(saved.payload()).containsEntry("sprint_id", sprintId).containsEntry("sprint_name", "Sprint 1");
    }

    @Test
    void listForIssueMapsEveryStoredEvent() {
        ActivityEvent event = ActivityEvent.of(projectId, issueId, ActivityEventTypes.ISSUE_CREATED, actorId,
                java.util.Map.of("title", "Title"));
        when(activityEventRepository.findAllByProjectIdAndIssueIdOrderByTimestampAsc(projectId, issueId))
                .thenReturn(List.of(event));

        List<ActivityEventResponse> result = activityService.listForIssue(projectId, issueId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).eventType()).isEqualTo(ActivityEventTypes.ISSUE_CREATED);
    }

    private ActivityEvent captureSaved() {
        ArgumentCaptor<ActivityEvent> captor = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventRepository).save(captor.capture());
        return captor.getValue();
    }

    private void verifyPushedToBoardTopic(String projectKey) {
        ArgumentCaptor<BoardUpdateMessage> captor = ArgumentCaptor.forClass(BoardUpdateMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/projects/" + projectKey + "/board"), captor.capture());
        assertThat(captor.getValue().projectKey()).isEqualTo(projectKey);
    }
}
