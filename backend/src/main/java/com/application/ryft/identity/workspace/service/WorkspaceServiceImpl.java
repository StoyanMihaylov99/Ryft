package com.application.ryft.identity.workspace.service;

import com.application.ryft.identity.workspace.dto.ChangeRoleRequest;
import com.application.ryft.identity.workspace.dto.CreateWorkspaceRequest;
import com.application.ryft.identity.workspace.dto.InviteRequest;
import com.application.ryft.identity.workspace.dto.WorkspaceMemberDTO;
import com.application.ryft.identity.workspace.exception.AlreadyWorkspaceMemberException;
import com.application.ryft.identity.workspace.exception.CannotAssignOwnerRoleException;
import com.application.ryft.identity.workspace.exception.CannotModifySelfRoleException;
import com.application.ryft.identity.workspace.exception.InsufficientWorkspaceRoleException;
import com.application.ryft.identity.workspace.exception.InviteTargetNotFoundException;
import com.application.ryft.identity.workspace.exception.NotAWorkspaceMemberException;
import com.application.ryft.identity.workspace.exception.WorkspaceAlreadySetUpException;
import com.application.ryft.identity.workspace.exception.WorkspaceMemberNotFoundException;
import com.application.ryft.identity.workspace.exception.WorkspaceNotSetUpException;
import com.application.ryft.identity.user.repository.UserRepository;
import com.application.ryft.identity.workspace.repository.WorkspaceMemberRepository;
import com.application.ryft.identity.workspace.repository.WorkspaceRepository;
import com.application.ryft.identity.user.entity.User;
import com.application.ryft.identity.workspace.entity.Workspace;
import com.application.ryft.identity.workspace.entity.WorkspaceMember;
import com.application.ryft.identity.workspace.entity.WorkspaceRole;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    public WorkspaceServiceImpl(WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository, UserRepository userRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public WorkspaceMemberDTO completeSetup(UUID callerId, CreateWorkspaceRequest request) {
        if (workspaceRepository.count() > 0) {
            throw new WorkspaceAlreadySetUpException();
        }
        User caller = userRepository.findById(callerId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + callerId));

        Workspace workspace = workspaceRepository.save(
                new Workspace(request.name().trim(), normalizeSlug(request.slug())));
        WorkspaceMember owner = new WorkspaceMember(workspace, caller, WorkspaceRole.OWNER);
        workspaceMemberRepository.save(owner);
        return toDTO(owner);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceMemberDTO> listMembers(UUID callerId) {
        Workspace workspace = requireWorkspace();
        requireMembership(workspace, callerId);
        return workspaceMemberRepository.findAllByWorkspaceIdOrderByJoinedAtAsc(workspace.getId()).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public WorkspaceMemberDTO invite(UUID callerId, InviteRequest request) {
        Workspace workspace = requireWorkspace();
        WorkspaceMember caller = requireMembership(workspace, callerId);
        requireOwnerOrAdmin(caller);
        if (request.role() == WorkspaceRole.OWNER) {
            throw new CannotAssignOwnerRoleException();
        }

        String email = normalizeEmail(request.email());
        User target = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InviteTargetNotFoundException(email));
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), target.getId()).isPresent()) {
            throw new AlreadyWorkspaceMemberException(email);
        }

        WorkspaceMember member = new WorkspaceMember(workspace, target, request.role());
        workspaceMemberRepository.save(member);
        return toDTO(member);
    }

    @Override
    @Transactional
    public WorkspaceMemberDTO changeRole(UUID callerId, UUID targetUserId, ChangeRoleRequest request) {
        Workspace workspace = requireWorkspace();
        WorkspaceMember caller = requireMembership(workspace, callerId);
        if (caller.getRole() != WorkspaceRole.OWNER) {
            throw new InsufficientWorkspaceRoleException("Only the workspace Owner can change member roles");
        }
        if (callerId.equals(targetUserId)) {
            throw new CannotModifySelfRoleException();
        }
        if (request.role() == WorkspaceRole.OWNER) {
            throw new CannotAssignOwnerRoleException();
        }

        WorkspaceMember target = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), targetUserId)
                .orElseThrow(WorkspaceMemberNotFoundException::new);
        target.setRole(request.role());
        return toDTO(target);
    }

    private Workspace requireWorkspace() {
        return workspaceRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(WorkspaceNotSetUpException::new);
    }

    private WorkspaceMember requireMembership(Workspace workspace, UUID userId) {
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), userId)
                .orElseThrow(NotAWorkspaceMemberException::new);
    }

    private void requireOwnerOrAdmin(WorkspaceMember member) {
        if (member.getRole() != WorkspaceRole.OWNER && member.getRole() != WorkspaceRole.ADMIN) {
            throw new InsufficientWorkspaceRoleException("Only the workspace Owner or an Admin can invite members");
        }
    }

    private WorkspaceMemberDTO toDTO(WorkspaceMember member) {
        User user = member.getUser();
        return new WorkspaceMemberDTO(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl(),
                member.getRole(), member.getJoinedAt());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSlug(String slug) {
        return slug.trim().toLowerCase(Locale.ROOT);
    }
}
