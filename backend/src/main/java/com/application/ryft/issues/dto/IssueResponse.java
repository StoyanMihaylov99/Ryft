package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code labels}/{@code components} carry the full {@link LabelResponse}/{@link ComponentResponse}
 * (not just ids) so the frontend can render badges (name + color) directly from a board/list response
 * without a second round trip per issue — {@code IssueLabelingService} is what actually resolves them,
 * batched across a whole list response rather than per issue.
 */
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
        UUID parentId,
        List<LabelResponse> labels,
        List<ComponentResponse> components,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
) {

    public static IssueResponse from(Issue issue, List<LabelResponse> labels, List<ComponentResponse> components) {
        return new IssueResponse(issue.getId(), issue.getProjectId(), issue.getKey(), issue.getType(),
                issue.getTitle(), issue.getDescription(), issue.getStatus(), issue.getPriority(),
                issue.getAssigneeId(), issue.getReporterId(), issue.getSprintId(), issue.getStoryPoints(),
                issue.getParentIssueId(), labels, components, issue.getCreatedAt(), issue.getUpdatedAt(),
                issue.getResolvedAt());
    }
}
