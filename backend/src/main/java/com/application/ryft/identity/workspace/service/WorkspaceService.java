package com.application.ryft.identity.workspace.service;

import com.application.ryft.identity.workspace.dto.ChangeRoleRequest;
import com.application.ryft.identity.workspace.dto.CreateWorkspaceRequest;
import com.application.ryft.identity.workspace.dto.InviteRequest;
import com.application.ryft.identity.workspace.dto.WorkspaceMemberResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceService {

    /**
     * Creates the single v1 workspace and makes {@code callerId} its Owner. Only succeeds while no
     * workspace exists yet — whichever authenticated user calls this first "wins" and becomes Owner;
     * every call after that fails with {@link com.application.ryft.identity.workspace.exception.WorkspaceAlreadySetUpException}.
     */
    WorkspaceMemberResponse completeSetup(UUID callerId, CreateWorkspaceRequest request);

    Optional<UUID> getCurrentWorkspaceId();

    List<WorkspaceMemberResponse> listMembers(UUID callerId);

    WorkspaceMemberResponse invite(UUID callerId, InviteRequest request);

    WorkspaceMemberResponse changeRole(UUID callerId, UUID targetUserId, ChangeRoleRequest request);
}
