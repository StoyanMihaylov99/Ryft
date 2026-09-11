package com.application.ryft.identity.dto;

import com.application.ryft.identity.repository.entity.WorkspaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteRequest(
        @NotBlank @Email String email,
        @NotNull WorkspaceRole role
) {
}
