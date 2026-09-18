package com.application.ryft.issues.dto;

import com.application.ryft.issues.entity.IssuePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Slimmer than {@link CreateIssueRequest}: {@code type} (always {@code SUBTASK}) and {@code parentId}
 * are both implied by the {@code POST /issues/{issueKey}/subtasks} URL, not accepted in the body, so
 * there's no way to pass a conflicting one. {@code priority} defaults to {@code MEDIUM} when omitted,
 * same as on the main create endpoint.
 */
public record CreateSubtaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description,
        IssuePriority priority,
        UUID assigneeId
) {
}
