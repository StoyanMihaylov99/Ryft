package com.application.ryft.workflow.dto;

import com.application.ryft.workflow.entity.StatusCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * One status row within {@link UpdateWorkflowSchemeRequest}. {@code id == null} means "create a new
 * status"; an existing status whose id is present in the scheme but missing from the submitted list is
 * deleted (rejected with {@code WorkflowStatusInUseException} if any Issue still references it).
 */
public record WorkflowStatusEdit(
        UUID id,
        @NotBlank String name,
        @NotNull StatusCategory category,
        int sortOrder
) {
}
