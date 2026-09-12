package com.application.ryft.projects.service;

import com.application.ryft.projects.dto.AddProjectMemberRequest;
import com.application.ryft.projects.dto.ChangeProjectMemberRoleRequest;
import com.application.ryft.projects.dto.CreateProjectRequest;
import com.application.ryft.projects.dto.ProjectDTO;
import com.application.ryft.projects.dto.ProjectMemberDTO;
import com.application.ryft.projects.dto.UpdateProjectRequest;
import java.util.List;
import java.util.UUID;

public interface ProjectService {

    /** Creates a project in the v1 workspace and makes {@code callerId} its Owner. */
    ProjectDTO create(UUID callerId, CreateProjectRequest request);

    /** Non-archived projects {@code callerId} is a member of. */
    List<ProjectDTO> listForCaller(UUID callerId);

    ProjectDTO get(UUID callerId, String projectKey);

    ProjectDTO update(UUID callerId, String projectKey, UpdateProjectRequest request);

    /** Soft-deletes the project (sets {@code archivedAt}); Owner only. */
    void archive(UUID callerId, String projectKey);

    List<ProjectMemberDTO> listMembers(UUID callerId, String projectKey);

    ProjectMemberDTO addMember(UUID callerId, String projectKey, AddProjectMemberRequest request);

    ProjectMemberDTO changeMemberRole(UUID callerId, String projectKey, UUID targetUserId,
            ChangeProjectMemberRoleRequest request);

    void removeMember(UUID callerId, String projectKey, UUID targetUserId);
}
