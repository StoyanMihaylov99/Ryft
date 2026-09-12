package com.application.ryft.projects.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.application.ryft.AbstractIntegrationTest;
import com.application.ryft.identity.auth.dto.RegisterRequest;
import com.application.ryft.identity.user.entity.User;
import com.application.ryft.identity.user.repository.UserRepository;
import com.application.ryft.identity.workspace.entity.Workspace;
import com.application.ryft.identity.workspace.repository.WorkspaceRepository;
import com.application.ryft.projects.dto.AddProjectMemberRequest;
import com.application.ryft.projects.dto.ChangeProjectMemberRoleRequest;
import com.application.ryft.projects.dto.CreateProjectRequest;
import com.application.ryft.projects.dto.ProjectDTO;
import com.application.ryft.projects.dto.UpdateProjectRequest;
import com.application.ryft.projects.entity.Project;
import com.application.ryft.projects.entity.ProjectMember;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Shares one Postgres container (and hence the same workspace row) across the whole test JVM, same as
 * {@code WorkspaceControllerIT} — see {@link #theWorkspace()}. Each test uses its own random project
 * key so tests never collide with each other's data.
 */
@AutoConfigureMockMvc
class ProjectControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueKey() {
        return "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String registerAndGetToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest(email, "password123", "Test User " + email);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private User userOf(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }

    private Workspace theWorkspace() {
        return workspaceRepository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(() -> workspaceRepository.save(new Workspace("Ryft", "ryft")));
    }

    private Project createProject(String key, User owner) {
        theWorkspace();
        Project project = projectRepository.save(new Project(theWorkspace().getId(), key, "Project " + key, null));
        projectMemberRepository.save(new ProjectMember(project, owner.getId(), ProjectRole.OWNER));
        return project;
    }

    private void addMembership(Project project, User user, ProjectRole role) {
        projectMemberRepository.save(new ProjectMember(project, user.getId(), role));
    }

    @Test
    void createProjectMakesCallerOwner() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();

        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequest(key, "Tracker", "desc"))))
                .andExpect(status().isCreated())
                .andReturn();

        ProjectDTO project = objectMapper.readValue(result.getResponse().getContentAsString(), ProjectDTO.class);
        assertThat(project.key()).isEqualTo(key);
        assertThat(projectMemberRepository.findByProjectIdAndUserId(project.id(), userOf(email).getId()))
                .isPresent()
                .get()
                .satisfies(member -> assertThat(member.getRole()).isEqualTo(ProjectRole.OWNER));
    }

    @Test
    void createProjectRejectsDuplicateKeyInWorkspace() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequest(key, "Tracker 2", null))))
                .andExpect(status().isConflict());
    }

    @Test
    void listReturnsOnlyCallersProjects() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        MvcResult result = mockMvc.perform(get("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        List<ProjectDTO> projects = List.of(
                objectMapper.readValue(result.getResponse().getContentAsString(), ProjectDTO[].class));
        assertThat(projects).anySatisfy(p -> assertThat(p.key()).isEqualTo(key));
    }

    @Test
    void getByNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String key = uniqueKey();
        createProject(key, userOf2(ownerEmail));

        String outsiderEmail = uniqueEmail();
        String outsiderToken = registerAndGetToken(outsiderEmail);

        mockMvc.perform(get("/api/v1/projects/{projectKey}", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }

    private User userOf2(String email) throws Exception {
        registerAndGetToken(email);
        return userOf(email);
    }

    @Test
    void getOfUnknownKeyReturns404() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);

        mockMvc.perform(get("/api/v1/projects/{projectKey}", uniqueKey())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateByMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        mockMvc.perform(patch("/api/v1/projects/{projectKey}", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProjectRequest("New name", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateByOwnerSucceeds() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        mockMvc.perform(patch("/api/v1/projects/{projectKey}", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProjectRequest("New name", null))))
                .andExpect(status().isOk());

        assertThat(projectRepository.findByWorkspaceIdAndKey(theWorkspace().getId(), key))
                .isPresent()
                .get()
                .satisfies(p -> assertThat(p.getName()).isEqualTo("New name"));
    }

    @Test
    void archiveByAdminReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String adminEmail = uniqueEmail();
        String adminToken = registerAndGetToken(adminEmail);
        addMembership(project, userOf(adminEmail), ProjectRole.ADMIN);

        mockMvc.perform(delete("/api/v1/projects/{projectKey}", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void archiveByOwnerSucceeds() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        mockMvc.perform(delete("/api/v1/projects/{projectKey}", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(projectRepository.findById(project.getId()).orElseThrow().isArchived()).isTrue();
    }

    @Test
    void ownerAddsMemberByEmail() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String targetEmail = uniqueEmail();
        registerAndGetToken(targetEmail);

        mockMvc.perform(post("/api/v1/projects/{projectKey}/members", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddProjectMemberRequest(targetEmail, ProjectRole.VIEWER))))
                .andExpect(status().isCreated());

        assertThat(projectMemberRepository.findByProjectIdAndUserId(project.getId(), userOf(targetEmail).getId()))
                .isPresent()
                .get()
                .satisfies(member -> assertThat(member.getRole()).isEqualTo(ProjectRole.VIEWER));
    }

    @Test
    void addMemberTargetingOwnerRoleReturns400() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        String targetEmail = uniqueEmail();
        registerAndGetToken(targetEmail);

        mockMvc.perform(post("/api/v1/projects/{projectKey}/members", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddProjectMemberRequest(targetEmail, ProjectRole.OWNER))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerChangesMemberRoleSuccessfully() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String targetEmail = uniqueEmail();
        registerAndGetToken(targetEmail);
        User target = userOf(targetEmail);
        addMembership(project, target, ProjectRole.MEMBER);

        mockMvc.perform(patch("/api/v1/projects/{projectKey}/members/{userId}", key, target.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeProjectMemberRoleRequest(ProjectRole.ADMIN))))
                .andExpect(status().isOk());

        assertThat(projectMemberRepository.findByProjectIdAndUserId(project.getId(), target.getId()))
                .isPresent()
                .get()
                .satisfies(member -> assertThat(member.getRole()).isEqualTo(ProjectRole.ADMIN));
    }

    @Test
    void ownerCannotRemoveSelf() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        User owner = userOf(ownerEmail);
        createProject(key, owner);

        mockMvc.perform(delete("/api/v1/projects/{projectKey}/members/{userId}", key, owner.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerRemovesMemberSuccessfully() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String targetEmail = uniqueEmail();
        registerAndGetToken(targetEmail);
        User target = userOf(targetEmail);
        addMembership(project, target, ProjectRole.MEMBER);

        mockMvc.perform(delete("/api/v1/projects/{projectKey}/members/{userId}", key, target.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(projectMemberRepository.findByProjectIdAndUserId(project.getId(), target.getId())).isEmpty();
    }
}
