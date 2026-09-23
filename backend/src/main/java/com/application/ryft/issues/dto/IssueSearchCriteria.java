package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.IssueType;
import java.util.List;
import java.util.UUID;

/**
 * The AND-combining structured filter that {@code ProjectIssuesController}'s GET
 * {@code sprintId}/{@code epicId}/{@code labelId}/{@code componentId} params deferred (see that
 * controller's javadoc: combining status/assignee/type filters with AND semantics needed a dynamic
 * query, kept out of scope there and picked up here instead). {@code IssueService#search} builds one
 * dynamic query from this rather than the precedence chain that endpoint still uses.
 *
 * <p>Every field is optional: {@code null} or an empty list means "don't filter on this field". Values
 * within one field are OR'd together (e.g. {@code assigneeIds = [A, B]} matches issues assigned to A or
 * B); different fields are AND'd together (e.g. {@code assigneeIds} AND {@code statusIds} means "assigned
 * to A or B, and currently in status S"). SUBTASK issues are always excluded unconditionally — same rule
 * as every other list method on {@code IssueService}; there is no field here to opt back into seeing them.
 *
 * <p>{@code text}: a free-text query AND'd in alongside every other field (a blank/{@code null} value
 * means "don't filter on it", the same convention as the list fields above). Matched against
 * {@code title}/{@code description} via Postgres full-text search — see
 * {@code IssueRepository#searchIssueIdsByText} for the actual {@code to_tsvector}/{@code plainto_tsquery}
 * query and {@code IssueSpecifications} for how its result is folded into this criteria's {@code Specification}.
 */
public record IssueSearchCriteria(
        List<UUID> assigneeIds,
        List<UUID> statusIds,
        List<UUID> labelIds,
        List<UUID> componentIds,
        List<IssueType> types,
        List<UUID> sprintIds,
        String text
) {
}
