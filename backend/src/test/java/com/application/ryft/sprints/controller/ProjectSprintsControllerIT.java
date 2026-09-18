package com.application.ryft.sprints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.application.ryft.AbstractIntegrationTest;
import com.application.ryft.identity.auth.dto.RegisterRequest;
import com.application.ryft.identity.user.entity.User;
import com.application.ryft.identity.user.repository.UserRepository;
import com.application.ryft.identity.workspace.entity.Workspace;
import com.application.ryft.identity.workspace.repository.WorkspaceRepository;
import com.application.ryft.projects.entity.Project;
import com.application.ryft.projects.entity.ProjectMember;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
import com.application.ryft.sprints.dto.CreateSprintRequest;
import com.application.ryft.sprints.dto.SprintResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** Shares one Postgres container across the whole test JVM, same as the other *IT classes. */
@AutoConfigureMockMvc
class ProjectSprintsControllerIT extends AbstractIntegrationTest {

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
        Project project = projectRepository.save(new Project(theWorkspace().getId(), key, "Project " + key, null));
        projectMemberRepository.save(new ProjectMember(project, owner.getId(), ProjectRole.OWNER));
        return project;
    }

    private void addMembership(Project project, User user, ProjectRole role) {
        projectMemberRepository.save(new ProjectMember(project, user.getId(), role));
    }

    @Test
    void createAndListSprintsSucceedsForOwner() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/sprints", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSprintRequest("Sprint 1", "Goal", LocalDate.of(2026, 1, 1),
                                        LocalDate.of(2026, 1, 14)))))
                .andExpect(status().isCreated())
                .andReturn();
        SprintResponse sprint = objectMapper.readValue(created.getResponse().getContentAsString(), SprintResponse.class);
        assertThat(sprint.name()).isEqualTo("Sprint 1");
        assertThat(sprint.goal()).isEqualTo("Goal");

        MvcResult listed = mockMvc.perform(get("/api/v1/projects/{projectKey}/sprints", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        SprintResponse[] sprints = objectMapper.readValue(listed.getResponse().getContentAsString(), SprintResponse[].class);
        assertThat(sprints).hasSize(1);
        assertThat(sprints[0].id()).isEqualTo(sprint.id());
    }

    @Test
    void createSprintByNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        String outsiderToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(post("/api/v1/projects/{projectKey}/sprints", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSprintRequest("Sprint 1", null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSprintByPlainMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        mockMvc.perform(post("/api/v1/projects/{projectKey}/sprints", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSprintRequest("Sprint 1", null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSprintForUnknownProjectReturns404() throws Exception {
        String token = registerAndGetToken(uniqueEmail());

        mockMvc.perform(post("/api/v1/projects/{projectKey}/sprints", uniqueKey())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSprintRequest("Sprint 1", null, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void createSprintWithEndDateBeforeStartDateReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        mockMvc.perform(post("/api/v1/projects/{projectKey}/sprints", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSprintRequest("Sprint 1", null, LocalDate.of(2026, 1, 10),
                                        LocalDate.of(2026, 1, 1)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSprintsByNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        String outsiderToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/v1/projects/{projectKey}/sprints", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }
}
