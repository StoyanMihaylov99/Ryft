package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.IssuePriority;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Partial update: a null field is left unchanged. There is currently no way to clear an assignee,
 * story points, or parent link through this endpoint (null means "don't touch it") — set a new value
 * instead. {@code parentId}, when provided, is subject to the same type-dependent parent-link rules as
 * on create (see IssueServiceImpl): a STORY/TASK/BUG's parentId must resolve to an EPIC, a SUBTASK's
 * must resolve to a STORY/TASK/BUG, and an EPIC's is rejected outright. There is no {@code type} field:
 * an issue's type is fixed at creation and never changes via PATCH.
 *
 * <p>{@code labelIds}/{@code componentIds} are the one departure from "null means don't touch": since
 * they're multi-select fields, {@code null} still means "leave the existing set unchanged", but an
 * explicit empty list ({@code []}) means "clear all labels/components" — a real, distinguishable
 * request the client can send, unlike a scalar field such as {@code assigneeId} where JSON has no way
 * to say "empty" separately from "absent". Every id, when the list is non-null, must resolve to a
 * Label/Component in this issue's own project (400 otherwise, see {@code IssueServiceImpl}).
 */
public record UpdateIssueRequest(
        @Size(max = 200) String title,
        @Size(max = 10000) String description,
        IssuePriority priority,
        UUID assigneeId,
        @PositiveOrZero Integer storyPoints,
        UUID parentId,
        List<UUID> labelIds,
        List<UUID> componentIds
) {
}
