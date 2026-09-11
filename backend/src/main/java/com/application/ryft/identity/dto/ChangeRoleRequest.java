package com.application.ryft.identity.dto;

import com.application.ryft.identity.repository.entity.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull WorkspaceRole role
) {
}
