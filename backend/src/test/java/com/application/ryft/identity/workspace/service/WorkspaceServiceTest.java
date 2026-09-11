package com.application.ryft.identity.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private UserRepository userRepository;

    private WorkspaceServiceImpl workspaceService;

    private final Workspace workspace = new Workspace("Ryft", "ryft");
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workspaceService = new WorkspaceServiceImpl(workspaceRepository, workspaceMemberRepository, userRepository);
    }

    private void stubExistingWorkspace() {
        when(workspaceRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(workspace));
    }

    @Test
    void completeSetupCreatesWorkspaceAndMakesCallerOwner() {
        when(workspaceRepository.count()).thenReturn(0L);
        User caller = new User("first@example.com", "hash", "First User", null);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(caller));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workspaceMemberRepository.save(any(WorkspaceMember.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceMemberDTO result = workspaceService.completeSetup(callerId,
                new CreateWorkspaceRequest("Acme Inc", "acme"));

        assertThat(result.role()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(result.email()).isEqualTo("first@example.com");
        ArgumentCaptor<Workspace> workspaceCaptor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceRepository).save(workspaceCaptor.capture());
        assertThat(workspaceCaptor.getValue().getName()).isEqualTo("Acme Inc");
        assertThat(workspaceCaptor.getValue().getSlug()).isEqualTo("acme");
    }

    @Test
    void completeSetupRejectsWhenAWorkspaceAlreadyExists() {
        when(workspaceRepository.count()).thenReturn(1L);

        assertThatThrownBy(() -> workspaceService.completeSetup(callerId,
                new CreateWorkspaceRequest("Acme Inc", "acme")))
                .isInstanceOf(WorkspaceAlreadySetUpException.class);
        verify(workspaceMemberRepository, never()).save(any());
    }

    @Test
    void listMembersRequiresAWorkspaceToExist() {
        when(workspaceRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.listMembers(callerId))
                .isInstanceOf(WorkspaceNotSetUpException.class);
    }

    @Test
    void listMembersRequiresCallerToBeAMember() {
        stubExistingWorkspace();
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), eq(callerId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.listMembers(callerId))
                .isInstanceOf(NotAWorkspaceMemberException.class);
    }

    @Test
    void listMembersReturnsAllMembersForAMember() {
        stubExistingWorkspace();
        WorkspaceMember caller = new WorkspaceMember(workspace,
                new User("caller@example.com", "hash", "Caller", null), WorkspaceRole.MEMBER);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(caller));
        when(workspaceMemberRepository.findAllByWorkspaceIdOrderByJoinedAtAsc(any())).thenReturn(List.of(caller));

        List<WorkspaceMemberDTO> members = workspaceService.listMembers(callerId);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).email()).isEqualTo("caller@example.com");
    }

    @Test
    void inviteByOwnerSucceeds() {
        stubExistingWorkspace();
        WorkspaceMember owner = new WorkspaceMember(workspace,
                new User("owner@example.com", "hash", "Owner", null), WorkspaceRole.OWNER);
        User target = new User("target@example.com", "hash", "Target", null);
        when(userRepository.findByEmailIgnoreCase("target@example.com")).thenReturn(Optional.of(target));
        // First invocation resolves the caller's own membership (must be found, Owner); second
        // resolves the target's membership check in invite() (must be empty, not already a member).
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), any())).thenReturn(Optional.of(owner))
                .thenReturn(Optional.empty());
        when(workspaceMemberRepository.save(any(WorkspaceMember.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceMemberDTO result = workspaceService.invite(callerId,
                new InviteRequest("target@example.com", WorkspaceRole.MEMBER));

        assertThat(result.email()).isEqualTo("target@example.com");
        assertThat(result.role()).isEqualTo(WorkspaceRole.MEMBER);
    }

    @Test
    void inviteByPlainMemberIsRejected() {
        stubExistingWorkspace();
        WorkspaceMember caller = new WorkspaceMember(workspace,
                new User("member@example.com", "hash", "Member", null), WorkspaceRole.MEMBER);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> workspaceService.invite(callerId,
                new InviteRequest("target@example.com", WorkspaceRole.MEMBER)))
                .isInstanceOf(InsufficientWorkspaceRoleException.class);
        verify(userRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void inviteOfUnregisteredEmailThrows() {
        stubExistingWorkspace();
        WorkspaceMember owner = new WorkspaceMember(workspace,
                new User("owner@example.com", "hash", "Owner", null), WorkspaceRole.OWNER);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.invite(callerId,
                new InviteRequest("nobody@example.com", WorkspaceRole.MEMBER)))
                .isInstanceOf(InviteTargetNotFoundException.class);
    }

    @Test
    void inviteOfExistingMemberThrows() {
        stubExistingWorkspace();
        WorkspaceMember owner = new WorkspaceMember(workspace,
                new User("owner@example.com", "hash", "Owner", null), WorkspaceRole.OWNER);
        User target = new User("target@example.com", "hash", "Target", null);
        WorkspaceMember existingMembership = new WorkspaceMember(workspace, target, WorkspaceRole.MEMBER);
        when(userRepository.findByEmailIgnoreCase("target@example.com")).thenReturn(Optional.of(target));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), any())).thenReturn(Optional.of(owner))
                .thenReturn(Optional.of(existingMembership));

        assertThatThrownBy(() -> workspaceService.invite(callerId,
                new InviteRequest("target@example.com", WorkspaceRole.MEMBER)))
                .isInstanceOf(AlreadyWorkspaceMemberException.class);
    }

    @Test
    void inviteTargetingOwnerRoleThrows() {
        stubExistingWorkspace();
        WorkspaceMember owner = new WorkspaceMember(workspace,
                new User("owner@example.com", "hash", "Owner", null), WorkspaceRole.OWNER);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> workspaceService.invite(callerId,
                new InviteRequest("target@example.com", WorkspaceRole.OWNER)))
                .isInstanceOf(CannotAssignOwnerRoleException.class);
        verify(userRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void changeRoleByOwnerSucceeds() {
        stubExistingWorkspace();
        UUID targetId = UUID.randomUUID();
        WorkspaceMember owner = new WorkspaceMember(workspace,
                new User("owner@example.com", "hash", "Owner", null), WorkspaceRole.OWNER);
        WorkspaceMember target = new WorkspaceMember(workspace,
                new User("target@example.com", "hash", "Target", null), WorkspaceRole.MEMBER);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), eq(targetId))).thenReturn(Optional.of(target));

        WorkspaceMemberDTO result = workspaceService.changeRole(callerId, targetId,
                new ChangeRoleRequest(WorkspaceRole.ADMIN));

        assertThat(result.role()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    void changeRoleByNonOwnerIsRejected() {
        stubExistingWorkspace();
        UUID targetId = UUID.randomUUID();
        WorkspaceMember caller = new WorkspaceMember(workspace,
                new User("admin@example.com", "hash", "Admin", null), WorkspaceRole.ADMIN);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> workspaceService.changeRole(callerId, targetId,
                new ChangeRoleRequest(WorkspaceRole.MEMBER)))
                .isInstanceOf(InsufficientWorkspaceRoleException.class);
    }

    @Test
    void changeOwnRoleIsRejected() {
        stubExistingWorkspace();
        WorkspaceMember owner = new WorkspaceMember(workspace,
                new User("owner@example.com", "hash", "Owner", null), WorkspaceRole.OWNER);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> workspaceService.changeRole(callerId, callerId,
                new ChangeRoleRequest(WorkspaceRole.MEMBER)))
                .isInstanceOf(CannotModifySelfRoleException.class);
    }

    @Test
    void changeRoleToOwnerIsRejected() {
        stubExistingWorkspace();
        UUID targetId = UUID.randomUUID();
        WorkspaceMember owner = new WorkspaceMember(workspace,
                new User("owner@example.com", "hash", "Owner", null), WorkspaceRole.OWNER);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> workspaceService.changeRole(callerId, targetId,
                new ChangeRoleRequest(WorkspaceRole.OWNER)))
                .isInstanceOf(CannotAssignOwnerRoleException.class);
    }

    @Test
    void changeRoleOfNonMemberThrows() {
        stubExistingWorkspace();
        UUID targetId = UUID.randomUUID();
        WorkspaceMember owner = new WorkspaceMember(workspace,
                new User("owner@example.com", "hash", "Owner", null), WorkspaceRole.OWNER);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), eq(targetId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.changeRole(callerId, targetId,
                new ChangeRoleRequest(WorkspaceRole.MEMBER)))
                .isInstanceOf(WorkspaceMemberNotFoundException.class);
    }
}
