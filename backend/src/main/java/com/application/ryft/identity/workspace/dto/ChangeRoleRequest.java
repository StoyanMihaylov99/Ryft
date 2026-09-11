package com.application.ryft.identity.workspace.dto;

import com.application.ryft.identity.workspace.entity.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull WorkspaceRole role
) {
}
