package com.application.ryft.projects.service;

import com.application.ryft.projects.dto.AddProjectMemberRequest;
import com.application.ryft.projects.dto.ChangeProjectMemberRoleRequest;
import com.application.ryft.projects.dto.CreateProjectRequest;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.dto.ProjectMemberResponse;
import com.application.ryft.projects.dto.UpdateProjectRequest;
import com.application.ryft.projects.entity.ProjectRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectService {

    /** Creates a project in the v1 workspace and makes {@code callerId} its Owner. */
    ProjectResponse create(UUID callerId, CreateProjectRequest request);

    /** Non-archived projects {@code callerId} is a member of. */
    List<ProjectResponse> listForCaller(UUID callerId);

    ProjectResponse get(UUID callerId, String projectKey);

    ProjectResponse getById(UUID callerId, UUID projectId);

    /**
     * The caller's role on the given project, or {@link Optional#empty()} if they're not a member.
     * Direct query against {@code ProjectMemberRepository} — the single source of truth every module's
     * own {@code *ProjectAccess.isOwnerOrAdmin} is built on, replacing each one's previous
     * list-all-members-then-filter-client-side approach. Throws {@code ProjectNotFoundException} if the
     * project itself doesn't exist (same as {@link #get}), but never throws for a non-member caller.
     */
    Optional<ProjectRole> getRole(UUID callerId, String projectKey);

    ProjectResponse update(UUID callerId, String projectKey, UpdateProjectRequest request);

    /** Soft-deletes the project (sets {@code archivedAt}); Owner only. */
    void archive(UUID callerId, String projectKey);

    List<ProjectMemberResponse> listMembers(UUID callerId, String projectKey);

    ProjectMemberResponse addMember(UUID callerId, String projectKey, AddProjectMemberRequest request);

    ProjectMemberResponse changeMemberRole(UUID callerId, String projectKey, UUID targetUserId,
            ChangeProjectMemberRoleRequest request);

    void removeMember(UUID callerId, String projectKey, UUID targetUserId);
}
