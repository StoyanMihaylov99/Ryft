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
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.projects.entity.Project;
import com.application.ryft.projects.entity.ProjectMember;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
import com.application.ryft.sprints.dto.BurndownPoint;
import com.application.ryft.sprints.dto.BurndownResponse;
import com.application.ryft.sprints.dto.CreateSprintRequest;
import com.application.ryft.sprints.dto.MoveIssueToSprintRequest;
import com.application.ryft.sprints.dto.SprintResponse;
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
class BurndownControllerIT extends AbstractIntegrationTest {

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

    private void changeIssueStatus(String issueKey, IssueStatus newStatus, String token) throws Exception {
        mockMvc.perform(patch("/api/v1/issues/{issueKey}/status", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeIssueStatusRequest(newStatus))))
                .andExpect(status().isOk());
    }

    private SprintResponse startSprint(UUID sprintId, String token) throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/sprints/{sprintId}/start", sprintId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(started.getResponse().getContentAsString(), SprintResponse.class);
    }

    private BurndownResponse getBurndown(UUID sprintId, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sprints/{sprintId}/burndown", sprintId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), BurndownResponse.class);
    }

    @Test
    void burndownReflectsCompletedIssuePointsSubtractedFromCommittedPointsOnTodaysDay() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        LocalDate today = LocalDate.now();
        SprintResponse sprint = createSprint(key, token, new CreateSprintRequest("Sprint 1", "Goal",
                today.minusDays(2), today.plusDays(5)));

        IssueResponse doneIssue = createIssue(key, token, 5);
        IssueResponse unfinishedIssue = createIssue(key, token, 3);
        moveIssueToSprint(doneIssue.key(), sprint.id(), token);
        moveIssueToSprint(unfinishedIssue.key(), sprint.id(), token);

        SprintResponse activeSprint = startSprint(sprint.id(), token);
        assertThat(activeSprint.committedPoints()).isEqualTo(8);

        changeIssueStatus(doneIssue.key(), IssueStatus.DONE, token);

        BurndownResponse burndown = getBurndown(sprint.id(), token);

        assertThat(burndown.sprintId()).isEqualTo(sprint.id());
        assertThat(burndown.committedPoints()).isEqualTo(8);
        assertThat(burndown.startDate()).isEqualTo(today.minusDays(2));
        assertThat(burndown.endDate()).isEqualTo(today.plusDays(5));

        List<BurndownPoint> actual = burndown.actualBurndown();
        BurndownPoint todayPoint = actual.stream()
                .filter(point -> point.date().equals(today))
                .findFirst()
                .orElseThrow();
        assertThat(todayPoint.remainingPoints()).isEqualTo(3);

        assertThat(burndown.idealBurndown()).hasSize(8);
        assertThat(burndown.idealBurndown().get(0).remainingPoints()).isEqualTo(8);
        assertThat(burndown.idealBurndown().get(7).remainingPoints()).isEqualTo(0);
    }

    @Test
    void burndownOfUnknownSprintReturns404() throws Exception {
        String token = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/v1/sprints/{sprintId}/burndown", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void burndownByNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));
        LocalDate today = LocalDate.now();
        SprintResponse sprint = createSprint(key, ownerToken, new CreateSprintRequest("Sprint 1", null,
                today.minusDays(1), today.plusDays(5)));
        startSprint(sprint.id(), ownerToken);

        String outsiderToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/v1/sprints/{sprintId}/burndown", sprint.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void burndownOfPlannedSprintReturns409() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        LocalDate today = LocalDate.now();
        SprintResponse sprint = createSprint(key, token, new CreateSprintRequest("Sprint 1", null,
                today.minusDays(1), today.plusDays(5)));

        mockMvc.perform(get("/api/v1/sprints/{sprintId}/burndown", sprint.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());
    }
}
