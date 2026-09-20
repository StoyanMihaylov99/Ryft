package com.application.ryft.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Body of {@code PATCH /projects/{projectKey}/workflow} — a full statuses+transitions diff, not
 * per-item CRUD, since a scheme's statuses and transitions are one interdependent graph edited together
 * (see {@code WorkflowServiceImpl.updateScheme}'s javadoc for the v1 same-request-reference
 * restriction).
 */
public record UpdateWorkflowSchemeRequest(
        @NotNull List<@Valid WorkflowStatusEdit> statuses,
        @NotNull List<@Valid WorkflowTransitionEdit> transitions
) {
}
