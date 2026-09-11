package com.application.ryft.identity.workspace.dto;

import com.application.ryft.identity.workspace.entity.WorkspaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteRequest(
        @NotBlank @Email String email,
        @NotNull WorkspaceRole role
) {
}
