package com.application.ryft.sprints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.application.ryft.AbstractIntegrationTest;
import com.application.ryft.identity.auth.dto.RegisterRequest;
import com.application.ryft.identity.user.entity.User;
import com.application.ryft.identity.user.repository.UserRepository;
import com.application.ryft.identity.workspace.entity.Workspace;
import com.application.ryft.identity.workspace.repository.WorkspaceRepository;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.projects.entity.Project;
import com.application.ryft.projects.entity.ProjectMember;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
import com.application.ryft.sprints.dto.CreateSprintRequest;
import com.application.ryft.sprints.dto.MoveIssueToSprintRequest;
import com.application.ryft.sprints.dto.SprintResponse;
import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.entity.SprintState;
import com.application.ryft.sprints.repository.SprintRepository;
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
class IssueSprintControllerIT extends AbstractIntegrationTest {

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

    @Autowired
    private SprintRepository sprintRepository;

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

    private IssueResponse createIssue(String projectKey, String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);
    }

    private SprintResponse createSprint(String projectKey, String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/sprints", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSprintRequest("Sprint 1", null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(created.getResponse().getContentAsString(), SprintResponse.class);
    }

    @Test
    void moveIssueToSprintSucceedsForOwner() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse issue = createIssue(key, token);
        SprintResponse sprint = createSprint(key, token);

        MvcResult moved = mockMvc.perform(patch("/api/v1/issues/{issueKey}/sprint", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveIssueToSprintRequest(sprint.id()))))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse result = objectMapper.readValue(moved.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(result.sprintId()).isEqualTo(sprint.id());
    }

    @Test
    void moveIssueBackToBacklogSucceedsForOwner() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse issue = createIssue(key, token);
        SprintResponse sprint = createSprint(key, token);
        mockMvc.perform(patch("/api/v1/issues/{issueKey}/sprint", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveIssueToSprintRequest(sprint.id()))))
                .andExpect(status().isOk());

        MvcResult moved = mockMvc.perform(patch("/api/v1/issues/{issueKey}/sprint", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveIssueToSprintRequest(null))))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse result = objectMapper.readValue(moved.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(result.sprintId()).isNull();
    }

    @Test
    void moveIssueByNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));
        IssueResponse issue = createIssue(key, ownerToken);
        SprintResponse sprint = createSprint(key, ownerToken);

        String outsiderToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(patch("/api/v1/issues/{issueKey}/sprint", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveIssueToSprintRequest(sprint.id()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void moveIssueByPlainMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));
        IssueResponse issue = createIssue(key, ownerToken);
        SprintResponse sprint = createSprint(key, ownerToken);

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        mockMvc.perform(patch("/api/v1/issues/{issueKey}/sprint", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveIssueToSprintRequest(sprint.id()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void moveUnknownIssueReturns404() throws Exception {
        String token = registerAndGetToken(uniqueEmail());

        mockMvc.perform(patch("/api/v1/issues/{issueKey}/sprint", "NOPE-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveIssueToSprintRequest(UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveIntoUnknownSprintReturns404() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse issue = createIssue(key, token);

        mockMvc.perform(patch("/api/v1/issues/{issueKey}/sprint", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveIssueToSprintRequest(UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveIntoSprintFromDifferentProjectReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String keyA = uniqueKey();
        createProject(keyA, userOf(email));
        IssueResponse issue = createIssue(keyA, token);

        String keyB = uniqueKey();
        createProject(keyB, userOf(email));
        SprintResponse sprintInOtherProject = createSprint(keyB, token);

        mockMvc.perform(patch("/api/v1/issues/{issueKey}/sprint", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveIssueToSprintRequest(sprintInOtherProject.id()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moveIntoCompletedSprintReturns409() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse issue = createIssue(key, token);
        SprintResponse sprint = createSprint(key, token);
        Sprint entity = sprintRepository.findById(sprint.id()).orElseThrow();
        entity.setState(SprintState.COMPLETED);
        sprintRepository.save(entity);

        mockMvc.perform(patch("/api/v1/issues/{issueKey}/sprint", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveIssueToSprintRequest(sprint.id()))))
                .andExpect(status().isConflict());
    }
}
