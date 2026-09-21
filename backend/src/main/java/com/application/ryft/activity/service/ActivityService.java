package com.application.ryft.activity.service;

import com.application.ryft.activity.dto.ActivityEventResponse;
import java.util.List;
import java.util.UUID;

/**
 * A true leaf module: every method here takes only primitives/ids the caller already has, never a
 * type from another module (no {@code issues}/{@code sprints} entity or DTO). This is what lets
 * {@code activity} be called from anywhere in the codebase without ever depending back on the caller.
 */
public interface ActivityService {

    void recordIssueCreated(UUID projectId, String projectKey, UUID issueId, UUID actorId, String title,
            String issueType);

    void recordIssueStatusChanged(UUID projectId, String projectKey, UUID issueId, UUID actorId, String fromStatus,
            String toStatus);

    void recordIssueAssigneeChanged(UUID projectId, String projectKey, UUID issueId, UUID actorId,
            UUID previousAssigneeId, UUID newAssigneeId);

    void recordCommentAdded(UUID projectId, String projectKey, UUID issueId, UUID actorId, UUID commentId,
            String commentBody);

    void recordCommentUpdated(UUID projectId, String projectKey, UUID issueId, UUID actorId, UUID commentId,
            String commentBody);

    void recordCommentDeleted(UUID projectId, String projectKey, UUID issueId, UUID actorId, UUID commentId);

    void recordSprintStarted(UUID projectId, String projectKey, UUID sprintId, UUID actorId, String sprintName);

    void recordSprintCompleted(UUID projectId, String projectKey, UUID sprintId, UUID actorId, String sprintName);

    List<ActivityEventResponse> listForIssue(UUID projectId, UUID issueId);
}
