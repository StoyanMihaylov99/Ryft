package com.application.ryft.sprints.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.application.ryft.issues.dto.ChangeIssueStatusRequest;
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
import com.application.ryft.sprints.dto.SprintBoardResponse;
import com.application.ryft.sprints.dto.SprintResponse;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.entity.StatusCategory;
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

/**
 * Shares one Postgres container across the whole test JVM, same as the other *IT classes. Each test
 * uses a freshly created project, so its first {@code GET .../board/sprint} call is genuinely that
 * project's first-ever board view — the exact scenario that regresses if this endpoint's service
 * method is ever marked {@code readOnly = true} (see {@code SprintBoardServiceImpl.getBoard}'s
 * javadoc and {@code ARCHITECTURE.md}'s lazy-write section).
 */
@AutoConfigureMockMvc
class SprintBoardControllerIT extends AbstractIntegrationTest {

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

    private SprintResponse createSprint(String projectKey, String token, CreateSprintRequest request) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/sprints", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(created.getResponse().getContentAsString(), SprintResponse.class);
    }

    private IssueResponse createIssue(String projectKey, String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);
    }

    private void moveIssueToSprint(String issueKey, UUID sprintId, String token) throws Exception {
        mockMvc.perform(patch("/api/v1/issues/{issueKey}/sprint", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveIssueToSprintRequest(sprintId))))
                .andExpect(status().isOk());
    }

    private void changeIssueStatus(String projectKey, String issueKey, StatusCategory newCategory, String token)
            throws Exception {
        UUID statusId = statusIdOf(projectKey, token, newCategory);
        mockMvc.perform(patch("/api/v1/issues/{issueKey}/status", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeIssueStatusRequest(statusId))))
                .andExpect(status().isOk());
    }

    private UUID statusIdOf(String projectKey, String token, StatusCategory category) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectKey}/workflow", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        WorkflowSchemeResponse scheme = objectMapper.readValue(result.getResponse().getContentAsString(),
                WorkflowSchemeResponse.class);
        return scheme.statuses().stream().filter(s -> s.category() == category).findFirst().orElseThrow().id();
    }

    private void startSprint(UUID sprintId, String token) throws Exception {
        mockMvc.perform(post("/api/v1/sprints/{sprintId}/start", sprintId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void sprintBoardGroupsIssuesByColumnAndReflectsStatusChanges() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        SprintResponse sprint = createSprint(key, token, new CreateSprintRequest("Sprint 1", "Goal",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14)));
        IssueResponse issue = createIssue(key, token);
        moveIssueToSprint(issue.key(), sprint.id(), token);
        startSprint(sprint.id(), token);

        MvcResult boardResult = mockMvc.perform(get("/api/v1/projects/{projectKey}/board/sprint", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        SprintBoardResponse board = objectMapper.readValue(boardResult.getResponse().getContentAsString(),
                SprintBoardResponse.class);
        assertThat(board.sprintId()).isEqualTo(sprint.id());
        assertThat(board.sprintName()).isEqualTo("Sprint 1");
        assertThat(board.columns()).hasSize(4);
        assertThat(board.columns().get(0).category()).isEqualTo(StatusCategory.TODO);
        assertThat(board.columns().get(0).issues()).extracting("key").containsExactly(issue.key());
        assertThat(board.columns().get(3).issues()).isEmpty();

        changeIssueStatus(key, issue.key(), StatusCategory.DONE, token);

        MvcResult movedBoardResult = mockMvc.perform(get("/api/v1/projects/{projectKey}/board/sprint", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        SprintBoardResponse movedBoard = objectMapper.readValue(movedBoardResult.getResponse().getContentAsString(),
                SprintBoardResponse.class);
        assertThat(movedBoard.columns().get(0).issues()).isEmpty();
        assertThat(movedBoard.columns().get(3).issues()).extracting("key").containsExactly(issue.key());
    }

    @Test
    void sprintBoardWithNoActiveSprintReturns404() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        createSprint(key, token, new CreateSprintRequest("Sprint 1", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14)));

        mockMvc.perform(get("/api/v1/projects/{projectKey}/board/sprint", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void sprintBoardWithNoSprintsAtAllReturns404() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        mockMvc.perform(get("/api/v1/projects/{projectKey}/board/sprint", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void sprintBoardByNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));
        SprintResponse sprint = createSprint(key, ownerToken, new CreateSprintRequest("Sprint 1", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14)));
        startSprint(sprint.id(), ownerToken);

        String outsiderToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/v1/projects/{projectKey}/board/sprint", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }
}
