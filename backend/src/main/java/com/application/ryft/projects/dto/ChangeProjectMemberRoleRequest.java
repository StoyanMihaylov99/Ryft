package com.application.ryft.projects.dto;

import com.application.ryft.projects.entity.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record ChangeProjectMemberRoleRequest(
        @NotNull ProjectRole role
) {
}
