package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code priority} defaults to {@code MEDIUM} when omitted. {@code parentId} is a generic parent link
 * whose required shape depends on {@code type} (see IssueServiceImpl for the enforcement): optional for
 * STORY/TASK/BUG, and must then point at an EPIC in the same project; forbidden for EPIC; required for
 * SUBTASK, and must then point at a STORY/TASK/BUG in the same project. Creating a SUBTASK is usually
 * simpler via {@code POST /issues/{issueKey}/subtasks} (see {@link CreateSubtaskRequest}), which
 * resolves {@code parentId} from the URL instead.
 */
public record CreateIssueRequest(
        @NotNull IssueType type,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description,
        IssuePriority priority,
        UUID assigneeId,
        @PositiveOrZero Integer storyPoints,
        UUID parentId
) {
}
