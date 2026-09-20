package com.application.ryft.workflow.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * One transition row within {@link UpdateWorkflowSchemeRequest}. {@code id == null} means "create a new
 * transition"; the whole list is a full replace of the scheme's transition graph — an existing
 * transition whose id is missing from the submitted list is deleted. {@code fromStatusId}/
 * {@code toStatusId} must both resolve to a status that already existed in the scheme *before* this
 * request (v1 simplification — a brand-new status and a transition touching it can't be added in the
 * same {@code PATCH}; see {@code WorkflowServiceImpl.updateScheme}'s javadoc).
 */
public record WorkflowTransitionEdit(
        UUID id,
        @NotNull UUID fromStatusId,
        @NotNull UUID toStatusId,
        String name
) {
}
