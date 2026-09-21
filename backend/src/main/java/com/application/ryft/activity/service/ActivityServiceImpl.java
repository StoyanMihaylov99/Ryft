package com.application.ryft.activity.service;

import com.application.ryft.activity.dto.ActivityEventResponse;
import com.application.ryft.activity.dto.BoardUpdateMessage;
import com.application.ryft.activity.entity.ActivityEvent;
import com.application.ryft.activity.repository.ActivityEventRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityServiceImpl implements ActivityService {

    /** Long comment bodies are truncated before being embedded in an activity payload — see DATA_MODEL.md. */
    private static final int EXCERPT_LENGTH = 140;

    private final ActivityEventRepository activityEventRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ActivityServiceImpl(ActivityEventRepository activityEventRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.activityEventRepository = activityEventRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void recordIssueCreated(UUID projectId, String projectKey, UUID issueId, UUID actorId, String title,
            String issueType) {
        record(projectId, projectKey, issueId, ActivityEventTypes.ISSUE_CREATED, actorId,
                Map.of("title", title, "issue_type", issueType));
    }

    @Override
    public void recordIssueStatusChanged(UUID projectId, String projectKey, UUID issueId, UUID actorId,
            String fromStatus, String toStatus) {
        record(projectId, projectKey, issueId, ActivityEventTypes.ISSUE_STATUS_CHANGED, actorId,
                Map.of("from_status", fromStatus, "to_status", toStatus));
    }

    @Override
    public void recordIssueAssigneeChanged(UUID projectId, String projectKey, UUID issueId, UUID actorId,
            UUID previousAssigneeId, UUID newAssigneeId) {
        // HashMap, not Map.of: previousAssigneeId is null when the issue had no assignee before this change.
        Map<String, Object> payload = new HashMap<>();
        payload.put("previous_assignee_id", previousAssigneeId);
        payload.put("new_assignee_id", newAssigneeId);
        record(projectId, projectKey, issueId, ActivityEventTypes.ISSUE_ASSIGNEE_CHANGED, actorId, payload);
    }

    @Override
    public void recordCommentAdded(UUID projectId, String projectKey, UUID issueId, UUID actorId, UUID commentId,
            String commentBody) {
        record(projectId, projectKey, issueId, ActivityEventTypes.COMMENT_ADDED, actorId,
                Map.of("comment_id", commentId, "excerpt", excerptOf(commentBody)));
    }

    @Override
    public void recordCommentUpdated(UUID projectId, String projectKey, UUID issueId, UUID actorId, UUID commentId,
            String commentBody) {
        record(projectId, projectKey, issueId, ActivityEventTypes.COMMENT_UPDATED, actorId,
                Map.of("comment_id", commentId, "excerpt", excerptOf(commentBody)));
    }

    @Override
    public void recordCommentDeleted(UUID projectId, String projectKey, UUID issueId, UUID actorId,
            UUID commentId) {
        record(projectId, projectKey, issueId, ActivityEventTypes.COMMENT_DELETED, actorId,
                Map.of("comment_id", commentId));
    }

    @Override
    public void recordSprintStarted(UUID projectId, String projectKey, UUID sprintId, UUID actorId,
            String sprintName) {
        record(projectId, projectKey, null, ActivityEventTypes.SPRINT_STARTED, actorId,
                Map.of("sprint_id", sprintId, "sprint_name", sprintName));
    }

    @Override
    public void recordSprintCompleted(UUID projectId, String projectKey, UUID sprintId, UUID actorId,
            String sprintName) {
        record(projectId, projectKey, null, ActivityEventTypes.SPRINT_COMPLETED, actorId,
                Map.of("sprint_id", sprintId, "sprint_name", sprintName));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityEventResponse> listForIssue(UUID projectId, UUID issueId) {
        return activityEventRepository.findAllByProjectIdAndIssueIdOrderByTimestampAsc(projectId, issueId).stream()
                .map(ActivityEventResponse::from)
                .toList();
    }

    private void record(UUID projectId, String projectKey, UUID issueId, String eventType, UUID actorId,
            Map<String, Object> payload) {
        ActivityEvent saved = activityEventRepository.save(
                ActivityEvent.of(projectId, issueId, eventType, actorId, payload));
        messagingTemplate.convertAndSend("/topic/projects/" + projectKey + "/board",
                BoardUpdateMessage.from(saved, projectKey));
    }

    private String excerptOf(String commentBody) {
        if (commentBody == null || commentBody.length() <= EXCERPT_LENGTH) {
            return commentBody;
        }
        return commentBody.substring(0, EXCERPT_LENGTH);
    }
}
