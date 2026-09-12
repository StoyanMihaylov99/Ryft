package com.application.ryft.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.identity.user.dto.UserDTO;
import com.application.ryft.identity.user.service.UserService;
import com.application.ryft.identity.workspace.service.WorkspaceService;
import com.application.ryft.projects.dto.AddProjectMemberRequest;
import com.application.ryft.projects.dto.ChangeProjectMemberRoleRequest;
import com.application.ryft.projects.dto.CreateProjectRequest;
import com.application.ryft.projects.dto.ProjectDTO;
import com.application.ryft.projects.dto.ProjectMemberDTO;
import com.application.ryft.projects.dto.UpdateProjectRequest;
import com.application.ryft.projects.entity.Project;
import com.application.ryft.projects.entity.ProjectMember;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.exception.AddMemberTargetNotFoundException;
import com.application.ryft.projects.exception.AlreadyProjectMemberException;
import com.application.ryft.projects.exception.CannotAssignOwnerRoleException;
import com.application.ryft.projects.exception.CannotModifySelfRoleException;
import com.application.ryft.projects.exception.CannotRemoveSelfException;
import com.application.ryft.projects.exception.InsufficientProjectRoleException;
import com.application.ryft.projects.exception.NotAProjectMemberException;
import com.application.ryft.projects.exception.ProjectKeyAlreadyExistsException;
import com.application.ryft.projects.exception.ProjectMemberNotFoundException;
import com.application.ryft.projects.exception.ProjectNotFoundException;
import com.application.ryft.projects.exception.WorkspaceNotReadyException;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
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
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private UserService userService;

    private ProjectServiceImpl projectService;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final Project project = new Project(workspaceId, "TRK", "Tracker", "desc");

    @BeforeEach
    void setUp() {
        projectService = new ProjectServiceImpl(projectRepository, projectMemberRepository, workspaceService, userService);
    }

    private void stubWorkspace() {
        when(workspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(workspaceId));
    }

    @Test
    void createSavesProjectAndMakesCallerOwner() {
        stubWorkspace();
        when(projectRepository.existsByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectDTO result = projectService.create(callerId, new CreateProjectRequest("trk", "Tracker", "desc"));

        assertThat(result.key()).isEqualTo("TRK");
        assertThat(result.workspaceId()).isEqualTo(workspaceId);
        ArgumentCaptor<ProjectMember> memberCaptor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(ProjectRole.OWNER);
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(callerId);
    }

    @Test
    void createRejectsWhenWorkspaceNotSetUp() {
        when(workspaceService.getCurrentWorkspaceId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.create(callerId, new CreateProjectRequest("TRK", "Tracker", null)))
                .isInstanceOf(WorkspaceNotReadyException.class);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateKey() {
        stubWorkspace();
        when(projectRepository.existsByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> projectService.create(callerId, new CreateProjectRequest("TRK", "Tracker", null)))
                .isInstanceOf(ProjectKeyAlreadyExistsException.class);
        verify(projectMemberRepository, never()).save(any());
    }

    @Test
    void getRequiresProjectToExist() {
        stubWorkspace();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.get(callerId, "trk"))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void getRequiresCallerToBeAMember() {
        stubWorkspace();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.get(callerId, "TRK"))
                .isInstanceOf(NotAProjectMemberException.class);
    }

    @Test
    void updateByMemberIsRejected() {
        stubWorkspace();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember member = new ProjectMember(project, callerId, ProjectRole.MEMBER);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> projectService.update(callerId, "TRK", new UpdateProjectRequest("New name", null)))
                .isInstanceOf(InsufficientProjectRoleException.class);
    }

    @Test
    void updateByAdminSucceeds() {
        stubWorkspace();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember admin = new ProjectMember(project, callerId, ProjectRole.ADMIN);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(admin));

        ProjectDTO result = projectService.update(callerId, "TRK", new UpdateProjectRequest("New name", "New desc"));

        assertThat(result.name()).isEqualTo("New name");
        assertThat(result.description()).isEqualTo("New desc");
    }

    @Test
    void archiveByNonOwnerIsRejected() {
        stubWorkspace();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember admin = new ProjectMember(project, callerId, ProjectRole.ADMIN);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> projectService.archive(callerId, "TRK"))
                .isInstanceOf(InsufficientProjectRoleException.class);
        assertThat(project.isArchived()).isFalse();
    }

    @Test
    void archiveByOwnerSucceeds() {
        stubWorkspace();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember owner = new ProjectMember(project, callerId, ProjectRole.OWNER);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));

        projectService.archive(callerId, "TRK");

        assertThat(project.isArchived()).isTrue();
    }

    @Test
    void listForCallerExcludesArchivedProjects() {
        Project archived = new Project(workspaceId, "OLD", "Old", null);
        archived.setArchivedAt(java.time.Instant.now());
        ProjectMember activeMembership = new ProjectMember(project, callerId, ProjectRole.OWNER);
        ProjectMember archivedMembership = new ProjectMember(archived, callerId, ProjectRole.OWNER);
        when(projectMemberRepository.findAllByUserIdOrderByAddedAtAsc(callerId))
                .thenReturn(List.of(activeMembership, archivedMembership));

        List<ProjectDTO> result = projectService.listForCaller(callerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).key()).isEqualTo("TRK");
    }

    @Test
    void addMemberByOwnerSucceeds() {
        stubWorkspace();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember owner = new ProjectMember(project, callerId, ProjectRole.OWNER);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));
        UUID targetId = UUID.randomUUID();
        UserDTO target = new UserDTO(targetId, "target@example.com", "Target", null);
        when(userService.findByEmail("target@example.com")).thenReturn(Optional.of(target));
        when(userService.getById(targetId)).thenReturn(target);
        when(projectMemberRepository.findByProjectIdAndUserId(project.getId(), targetId)).thenReturn(Optional.empty());
        when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectMemberDTO result = projectService.addMember(callerId, "TRK",
                new AddProjectMemberRequest("target@example.com", ProjectRole.MEMBER));

        assertThat(result.email()).isEqualTo("target@example.com");
        assertThat(result.role()).isEqualTo(ProjectRole.MEMBER);
    }

    @Test
    void addMemberByPlainMemberIsRejected() {
        stubWorkspace();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember member = new ProjectMember(project, callerId, ProjectRole.MEMBER);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> projectService.addMember(callerId, "TRK",
                new AddProjectMemberRequest("target@example.com", ProjectRole.MEMBER)))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(userService, never()).findByEmail(any());
    }

    @Test
    void addMemberTargetingOwnerRoleIsRejected() {
        stubWorkspace();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember owner = new ProjectMember(project, callerId, ProjectRole.OWNER);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> projectService.addMember(callerId, "TRK",
                new AddProjectMemberRequest("target@example.com", ProjectRole.OWNER)))
                .isInstanceOf(CannotAssignOwnerRoleException.class);
        verify(userService, never()).findByEmail(any());
    }

    @Test
    void addMemberOfUnregisteredEmailThrows() {
        stubWorkspace();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember owner = new ProjectMember(project, callerId, ProjectRole.OWNER);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));
        when(userService.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.addMember(callerId, "TRK",
                new AddProjectMemberRequest("nobody@example.com", ProjectRole.MEMBER)))
                .isInstanceOf(AddMemberTargetNotFoundException.class);
    }

    @Test
    void addMemberOfExistingMemberThrows() {
        stubWorkspace();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember owner = new ProjectMember(project, callerId, ProjectRole.OWNER);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));
        UUID targetId = UUID.randomUUID();
        UserDTO target = new UserDTO(targetId, "target@example.com", "Target", null);
        when(userService.findByEmail("target@example.com")).thenReturn(Optional.of(target));
        when(projectMemberRepository.findByProjectIdAndUserId(project.getId(), targetId))
                .thenReturn(Optional.of(new ProjectMember(project, targetId, ProjectRole.MEMBER)));

        assertThatThrownBy(() -> projectService.addMember(callerId, "TRK",
                new AddProjectMemberRequest("target@example.com", ProjectRole.MEMBER)))
                .isInstanceOf(AlreadyProjectMemberException.class);
    }

    @Test
    void changeMemberRoleByOwnerSucceeds() {
        stubWorkspace();
        UUID targetId = UUID.randomUUID();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember owner = new ProjectMember(project, callerId, ProjectRole.OWNER);
        ProjectMember target = new ProjectMember(project, targetId, ProjectRole.MEMBER);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(targetId))).thenReturn(Optional.of(target));
        when(userService.getById(targetId)).thenReturn(new UserDTO(targetId, "target@example.com", "Target", null));

        ProjectMemberDTO result = projectService.changeMemberRole(callerId, "TRK", targetId,
                new ChangeProjectMemberRoleRequest(ProjectRole.ADMIN));

        assertThat(result.role()).isEqualTo(ProjectRole.ADMIN);
    }

    @Test
    void changeOwnRoleIsRejected() {
        stubWorkspace();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember owner = new ProjectMember(project, callerId, ProjectRole.OWNER);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> projectService.changeMemberRole(callerId, "TRK", callerId,
                new ChangeProjectMemberRoleRequest(ProjectRole.MEMBER)))
                .isInstanceOf(CannotModifySelfRoleException.class);
    }

    @Test
    void changeRoleOfNonMemberThrows() {
        stubWorkspace();
        UUID targetId = UUID.randomUUID();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember owner = new ProjectMember(project, callerId, ProjectRole.OWNER);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(targetId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.changeMemberRole(callerId, "TRK", targetId,
                new ChangeProjectMemberRoleRequest(ProjectRole.MEMBER)))
                .isInstanceOf(ProjectMemberNotFoundException.class);
    }

    @Test
    void removeMemberByOwnerSucceeds() {
        stubWorkspace();
        UUID targetId = UUID.randomUUID();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember owner = new ProjectMember(project, callerId, ProjectRole.OWNER);
        ProjectMember target = new ProjectMember(project, targetId, ProjectRole.MEMBER);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(targetId))).thenReturn(Optional.of(target));

        projectService.removeMember(callerId, "TRK", targetId);

        verify(projectMemberRepository).delete(target);
    }

    @Test
    void removeSelfIsRejected() {
        stubWorkspace();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember owner = new ProjectMember(project, callerId, ProjectRole.OWNER);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> projectService.removeMember(callerId, "TRK", callerId))
                .isInstanceOf(CannotRemoveSelfException.class);
        verify(projectMemberRepository, never()).delete(any());
    }

    @Test
    void removeMemberByNonOwnerIsRejected() {
        stubWorkspace();
        UUID targetId = UUID.randomUUID();
        when(projectRepository.findByWorkspaceIdAndKey(workspaceId, "TRK")).thenReturn(Optional.of(project));
        ProjectMember admin = new ProjectMember(project, callerId, ProjectRole.ADMIN);
        when(projectMemberRepository.findByProjectIdAndUserId(any(), eq(callerId))).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> projectService.removeMember(callerId, "TRK", targetId))
                .isInstanceOf(InsufficientProjectRoleException.class);
    }
}
