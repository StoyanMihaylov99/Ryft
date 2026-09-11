package com.application.ryft.identity.workspace.service;

import com.application.ryft.identity.workspace.dto.ChangeRoleRequest;
import com.application.ryft.identity.workspace.dto.CreateWorkspaceRequest;
import com.application.ryft.identity.workspace.dto.InviteRequest;
import com.application.ryft.identity.workspace.dto.WorkspaceMemberDTO;
import java.util.List;
import java.util.UUID;

public interface WorkspaceService {

    /**
     * Creates the single v1 workspace and makes {@code callerId} its Owner. Only succeeds while no
     * workspace exists yet — whichever authenticated user calls this first "wins" and becomes Owner;
     * every call after that fails with {@link com.application.ryft.identity.workspace.exception.WorkspaceAlreadySetUpException}.
     */
    WorkspaceMemberDTO completeSetup(UUID callerId, CreateWorkspaceRequest request);

    List<WorkspaceMemberDTO> listMembers(UUID callerId);

    WorkspaceMemberDTO invite(UUID callerId, InviteRequest request);

    WorkspaceMemberDTO changeRole(UUID callerId, UUID targetUserId, ChangeRoleRequest request);
}
