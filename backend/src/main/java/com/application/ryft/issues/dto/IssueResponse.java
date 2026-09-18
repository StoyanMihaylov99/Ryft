package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import java.time.Instant;
import java.util.UUID;

public record IssueResponse(
        UUID id,
        UUID projectId,
        String key,
        IssueType type,
        String title,
        String description,
        IssueStatus status,
        IssuePriority priority,
        UUID assigneeId,
        UUID reporterId,
        UUID sprintId,
        Integer storyPoints,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
) {

    public static IssueResponse from(Issue issue) {
        return new IssueResponse(issue.getId(), issue.getProjectId(), issue.getKey(), issue.getType(),
                issue.getTitle(), issue.getDescription(), issue.getStatus(), issue.getPriority(),
                issue.getAssigneeId(), issue.getReporterId(), issue.getSprintId(), issue.getStoryPoints(),
                issue.getCreatedAt(), issue.getUpdatedAt(), issue.getResolvedAt());
    }
}
