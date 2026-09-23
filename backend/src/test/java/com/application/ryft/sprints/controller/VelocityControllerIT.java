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
import com.application.ryft.sprints.dto.SprintResponse;
import com.application.ryft.sprints.dto.VelocityResponse;
import com.application.ryft.sprints.dto.VelocitySprintPoint;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import java.time.LocalDate;
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

/** Shares one Postgres container across the whole test JVM, same as the other *IT classes. */
@AutoConfigureMockMvc
class VelocityControllerIT extends AbstractIntegrationTest {

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

    private SprintResponse createSprint(String projectKey, String token, CreateSprintRequest request) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/sprints", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(created.getResponse().getContentAsString(), SprintResponse.class);
    }

    private IssueResponse createIssue(String projectKey, String token, Integer storyPoints) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, storyPoints, null, null, null))))
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

    private SprintResponse startSprint(UUID sprintId, String token) throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/sprints/{sprintId}/start", sprintId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(started.getResponse().getContentAsString(), SprintResponse.class);
    }

    private SprintResponse completeSprint(UUID sprintId, String token) throws Exception {
        MvcResult completed = mockMvc.perform(post("/api/v1/sprints/{sprintId}/complete", sprintId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(completed.getResponse().getContentAsString(), SprintResponse.class);
    }

    /** Creates, starts, and completes a sprint with one DONE issue (worth {@code points}) and one issue
     * left unfinished — mirroring the real lifecycle so completedPoints only reflects the DONE issue. */
    private SprintResponse createCompletedSprint(String projectKey, String token, String name, int points)
            throws Exception {
        SprintResponse sprint = createSprint(projectKey, token, new CreateSprintRequest(name, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14)));
        IssueResponse doneIssue = createIssue(projectKey, token, points);
        IssueResponse unfinishedIssue = createIssue(projectKey, token, 99);
        moveIssueToSprint(doneIssue.key(), sprint.id(), token);
        moveIssueToSprint(unfinishedIssue.key(), sprint.id(), token);
        startSprint(sprint.id(), token);
        changeIssueStatus(projectKey, doneIssue.key(), StatusCategory.DONE, token);
        return completeSprint(sprint.id(), token);
    }

    private VelocityResponse getVelocity(String projectKey, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectKey}/velocity", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), VelocityResponse.class);
    }

    @Test
    void returnsCompletedSprintsChronologicallyWithCommittedAndCompletedPoints() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        SprintResponse first = createCompletedSprint(key, token, "Sprint 1", 5);
        SprintResponse second = createCompletedSprint(key, token, "Sprint 2", 8);

        VelocityResponse velocity = getVelocity(key, token);

        assertThat(velocity.projectKey()).isEqualTo(key);
        assertThat(velocity.sprints()).extracting(VelocitySprintPoint::sprintId)
                .containsExactly(first.id(), second.id());
        assertThat(velocity.sprints().get(0).committedPoints()).isEqualTo(104);
        assertThat(velocity.sprints().get(0).completedPoints()).isEqualTo(5);
        assertThat(velocity.sprints().get(1).committedPoints()).isEqualTo(107);
        assertThat(velocity.sprints().get(1).completedPoints()).isEqualTo(8);
    }

    @Test
    void projectWithNoCompletedSprintsReturnsEmptyList() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        createSprint(key, token, new CreateSprintRequest("Sprint 1", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14)));

        VelocityResponse velocity = getVelocity(key, token);

        assertThat(velocity.sprints()).isEmpty();
    }

    @Test
    void limitParamRestrictsToTheMostRecentTrailingSprints() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        createCompletedSprint(key, token, "Sprint 1", 1);
        SprintResponse second = createCompletedSprint(key, token, "Sprint 2", 2);
        SprintResponse third = createCompletedSprint(key, token, "Sprint 3", 3);

        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectKey}/velocity", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andReturn();
        VelocityResponse velocity = objectMapper.readValue(result.getResponse().getContentAsString(),
                VelocityResponse.class);

        assertThat(velocity.sprints()).extracting(VelocitySprintPoint::sprintId)
                .containsExactly(second.id(), third.id());
    }

    @Test
    void velocityOfUnknownProjectReturns404() throws Exception {
        String token = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/v1/projects/{projectKey}/velocity", "UNKNOWN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void velocityByNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        String outsiderToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/v1/projects/{projectKey}/velocity", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidLimitBelowMinimumReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        mockMvc.perform(get("/api/v1/projects/{projectKey}/velocity", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidLimitAboveMaximumReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        mockMvc.perform(get("/api/v1/projects/{projectKey}/velocity", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("limit", "21"))
                .andExpect(status().isBadRequest());
    }
}
