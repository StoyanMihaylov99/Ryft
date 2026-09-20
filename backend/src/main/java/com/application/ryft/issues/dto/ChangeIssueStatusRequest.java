package com.application.ryft.issues.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code statusId} must resolve to a {@code WorkflowStatus} in the issue's project scheme, and the move
 * from the issue's current status must be legal per that scheme's transition graph (see
 * {@code WorkflowService.isTransitionLegal}) — otherwise {@code IssueServiceImpl.changeStatus} rejects
 * it with {@code IllegalStatusTransitionException} (409). Replaced the old fixed {@code IssueStatus}
 * enum field once workflows became configurable (Phase 4).
 */
public record ChangeIssueStatusRequest(
        @NotNull UUID statusId
) {
}
