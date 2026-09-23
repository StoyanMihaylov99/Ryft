package com.application.ryft.search.dto;

import com.application.ryft.issues.entity.IssueType;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Wire-facing body of {@code POST /projects/{projectKey}/search}. Same fields as
 * {@code issues.dto.IssueSearchCriteria}, kept as its own type since DTOs stay owned by their
 * controller-boundary module rather than shared across modules — {@code ProjectSearchController} maps
 * this one-to-one onto {@code IssueSearchCriteria} before calling {@code IssueService#search}.
 *
 * <p>Every field is optional: {@code null} or an empty list means "don't filter on this field". Values
 * within one field are OR'd together (e.g. {@code assigneeIds: [A, B]} matches issues assigned to A or
 * B); different fields are AND'd together (e.g. {@code assigneeIds} together with {@code statusIds}
 * means "assigned to A or B, and currently in one of statusIds"). SUBTASK issues are never returned,
 * regardless of {@code types}.
 *
 * <p>{@code text}: a free-text query, AND'd in the same way as every field above (blank/{@code null}
 * means "don't filter on it"), matched against {@code title}/{@code description} via Postgres full-text
 * search — see {@code issues.dto.IssueSearchCriteria}'s javadoc. Capped at 200 characters, generous for a
 * search phrase but enough to reject a pathologically long query before it reaches the database.
 */
public record IssueSearchRequest(
        List<UUID> assigneeIds,
        List<UUID> statusIds,
        List<UUID> labelIds,
        List<UUID> componentIds,
        List<IssueType> types,
        List<UUID> sprintIds,
        @Size(max = 200) String text
) {
}
