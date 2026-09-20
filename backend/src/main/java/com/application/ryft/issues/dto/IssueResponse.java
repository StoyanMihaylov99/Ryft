package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code labels}/{@code components} carry the full {@link LabelResponse}/{@link ComponentResponse}
 * (not just ids) so the frontend can render badges (name + color) directly from a board/list response
 * without a second round trip per issue — {@code IssueLabelingService} is what actually resolves them,
 * batched across a whole list response rather than per issue.
 *
 * <p>{@code statusId}/{@code statusName}/{@code statusCategory} replace the old fixed {@code IssueStatus}
 * enum field now that workflows are configurable (Phase 4) — {@code statusId} is the source of truth
 * (a real {@code WorkflowStatus} row), {@code statusName}/{@code statusCategory} are denormalized onto
 * the response so the frontend doesn't need a second lookup just to render a column/badge. All three are
 * batch-resolved per project in {@code IssueLabelingService}, the same place labels/components are.
 *
 * <p>{@code callerCanEdit} is Owner/Admin, or Member-and-involved (assignee or reporter) — the one
 * permission the frontend can't derive from {@code ProjectResponse.callerRole} alone, since it also
 * depends on this specific issue. Delete- and status-change-permission are both derivable from
 * {@code callerRole} alone, so no extra field is needed for those. This is UX-only: backend enforcement
 * in {@code IssueServiceImpl} remains the actual trust boundary regardless of what this field says.
 */
public record IssueResponse(
        UUID id,
        UUID projectId,
        String key,
        IssueType type,
        String title,
        String description,
        UUID statusId,
        String statusName,
        StatusCategory statusCategory,
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
        Instant resolvedAt,
        boolean callerCanEdit
) {

    /**
     * Pre-Phase-4 shape, kept for call sites (mostly test fixtures unrelated to workflow/permission
     * behavior) that only care about the fixed {@link IssueStatus} enum — maps it onto the
     * identically-named {@link StatusCategory} constant (same enum-name bridge
     * {@code issues.service.BoardServiceImpl} used before this phase), leaves {@code statusId} unset,
     * and defaults {@code callerCanEdit} to {@code false}.
     */
    public IssueResponse(UUID id, UUID projectId, String key, IssueType type, String title, String description,
            IssueStatus status, IssuePriority priority, UUID assigneeId, UUID reporterId, UUID sprintId,
            Integer storyPoints, UUID parentId, List<LabelResponse> labels, List<ComponentResponse> components,
            Instant createdAt, Instant updatedAt, Instant resolvedAt) {
        this(id, projectId, key, type, title, description, null, status.name(),
                StatusCategory.valueOf(status.name()), priority, assigneeId, reporterId, sprintId, storyPoints,
                parentId, labels, components, createdAt, updatedAt, resolvedAt, false);
    }

    public static IssueResponse from(Issue issue, WorkflowStatusResponse status, List<LabelResponse> labels,
            List<ComponentResponse> components, boolean callerCanEdit) {
        return new IssueResponse(issue.getId(), issue.getProjectId(), issue.getKey(), issue.getType(),
                issue.getTitle(), issue.getDescription(), status.id(), status.name(), status.category(),
                issue.getPriority(), issue.getAssigneeId(), issue.getReporterId(), issue.getSprintId(),
                issue.getStoryPoints(), issue.getParentIssueId(), labels, components, issue.getCreatedAt(),
                issue.getUpdatedAt(), issue.getResolvedAt(), callerCanEdit);
    }
}
