package com.application.ryft.projects.service;

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
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceService workspaceService;
    private final UserService userService;

    public ProjectServiceImpl(ProjectRepository projectRepository, ProjectMemberRepository projectMemberRepository,
            WorkspaceService workspaceService, UserService userService) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.workspaceService = workspaceService;
        this.userService = userService;
    }

    @Override
    @Transactional
    public ProjectDTO create(UUID callerId, CreateProjectRequest request) {
        UUID workspaceId = requireWorkspaceId();
        String key = normalizeKey(request.key());
        if (projectRepository.existsByWorkspaceIdAndKey(workspaceId, key)) {
            throw new ProjectKeyAlreadyExistsException(key);
        }

        String description = request.description() == null ? null : request.description().trim();
        Project project = projectRepository.save(new Project(workspaceId, key, request.name().trim(), description));
        projectMemberRepository.save(new ProjectMember(project, callerId, ProjectRole.OWNER));
        return toDTO(project);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectDTO> listForCaller(UUID callerId) {
        return projectMemberRepository.findAllByUserIdOrderByAddedAtAsc(callerId).stream()
                .map(ProjectMember::getProject)
                .filter(project -> !project.isArchived())
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDTO get(UUID callerId, String projectKey) {
        Project project = requireProject(projectKey);
        requireMembership(project, callerId);
        return toDTO(project);
    }

    @Override
    @Transactional
    public ProjectDTO update(UUID callerId, String projectKey, UpdateProjectRequest request) {
        Project project = requireProject(projectKey);
        ProjectMember caller = requireMembership(project, callerId);
        requireOwnerOrAdmin(caller);

        if (request.name() != null && !request.name().isBlank()) {
            project.setName(request.name().trim());
        }
        if (request.description() != null) {
            project.setDescription(request.description().trim());
        }
        return toDTO(project);
    }

    @Override
    @Transactional
    public void archive(UUID callerId, String projectKey) {
        Project project = requireProject(projectKey);
        ProjectMember caller = requireMembership(project, callerId);
        requireOwner(caller);
        project.setArchivedAt(Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMemberDTO> listMembers(UUID callerId, String projectKey) {
        Project project = requireProject(projectKey);
        requireMembership(project, callerId);
        return projectMemberRepository.findAllByProjectIdOrderByAddedAtAsc(project.getId()).stream()
                .map(this::toMemberDTO)
                .toList();
    }

    @Override
    @Transactional
    public ProjectMemberDTO addMember(UUID callerId, String projectKey, AddProjectMemberRequest request) {
        Project project = requireProject(projectKey);
        ProjectMember caller = requireMembership(project, callerId);
        requireOwnerOrAdmin(caller);
        if (request.role() == ProjectRole.OWNER) {
            throw new CannotAssignOwnerRoleException();
        }

        String email = normalizeEmail(request.email());
        UserDTO target = userService.findByEmail(email)
                .orElseThrow(() -> new AddMemberTargetNotFoundException(email));
        if (projectMemberRepository.findByProjectIdAndUserId(project.getId(), target.id()).isPresent()) {
            throw new AlreadyProjectMemberException(email);
        }

        ProjectMember member = projectMemberRepository.save(new ProjectMember(project, target.id(), request.role()));
        return toMemberDTO(member);
    }

    @Override
    @Transactional
    public ProjectMemberDTO changeMemberRole(UUID callerId, String projectKey, UUID targetUserId,
            ChangeProjectMemberRoleRequest request) {
        Project project = requireProject(projectKey);
        ProjectMember caller = requireMembership(project, callerId);
        requireOwner(caller);
        if (callerId.equals(targetUserId)) {
            throw new CannotModifySelfRoleException();
        }
        if (request.role() == ProjectRole.OWNER) {
            throw new CannotAssignOwnerRoleException();
        }

        ProjectMember target = projectMemberRepository.findByProjectIdAndUserId(project.getId(), targetUserId)
                .orElseThrow(ProjectMemberNotFoundException::new);
        target.setRole(request.role());
        return toMemberDTO(target);
    }

    @Override
    @Transactional
    public void removeMember(UUID callerId, String projectKey, UUID targetUserId) {
        Project project = requireProject(projectKey);
        ProjectMember caller = requireMembership(project, callerId);
        requireOwner(caller);
        if (callerId.equals(targetUserId)) {
            throw new CannotRemoveSelfException();
        }

        ProjectMember target = projectMemberRepository.findByProjectIdAndUserId(project.getId(), targetUserId)
                .orElseThrow(ProjectMemberNotFoundException::new);
        projectMemberRepository.delete(target);
    }

    private UUID requireWorkspaceId() {
        return workspaceService.getCurrentWorkspaceId().orElseThrow(WorkspaceNotReadyException::new);
    }

    private Project requireProject(String projectKey) {
        UUID workspaceId = requireWorkspaceId();
        String key = normalizeKey(projectKey);
        return projectRepository.findByWorkspaceIdAndKey(workspaceId, key)
                .orElseThrow(() -> new ProjectNotFoundException(key));
    }

    private ProjectMember requireMembership(Project project, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(project.getId(), userId)
                .orElseThrow(NotAProjectMemberException::new);
    }

    private void requireOwnerOrAdmin(ProjectMember member) {
        if (member.getRole() != ProjectRole.OWNER && member.getRole() != ProjectRole.ADMIN) {
            throw new InsufficientProjectRoleException("Only the project Owner or an Admin can do this");
        }
    }

    private void requireOwner(ProjectMember member) {
        if (member.getRole() != ProjectRole.OWNER) {
            throw new InsufficientProjectRoleException("Only the project Owner can do this");
        }
    }

    private ProjectDTO toDTO(Project project) {
        return new ProjectDTO(project.getId(), project.getWorkspaceId(), project.getKey(), project.getName(),
                project.getDescription(), project.getCreatedAt(), project.getArchivedAt());
    }

    private ProjectMemberDTO toMemberDTO(ProjectMember member) {
        UserDTO user = userService.getById(member.getUserId());
        return new ProjectMemberDTO(user.id(), user.email(), user.displayName(), user.avatarUrl(),
                member.getRole(), member.getAddedAt());
    }

    private String normalizeKey(String key) {
        return key.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
