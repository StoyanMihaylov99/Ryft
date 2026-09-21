package com.application.ryft.issues.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.application.ryft.AbstractIntegrationTest;
import com.application.ryft.activity.dto.ActivityEventResponse;
import com.application.ryft.activity.service.ActivityEventTypes;
import com.application.ryft.identity.auth.dto.RegisterRequest;
import com.application.ryft.identity.user.entity.User;
import com.application.ryft.identity.user.repository.UserRepository;
import com.application.ryft.identity.workspace.entity.Workspace;
import com.application.ryft.identity.workspace.repository.WorkspaceRepository;
import com.application.ryft.issues.dto.ChangeIssueStatusRequest;
import com.application.ryft.issues.dto.CreateCommentRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.projects.entity.Project;
import com.application.ryft.projects.entity.ProjectMember;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import java.time.Duration;
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
 * Activity recording happens off the request thread (the triggering service publishes a
 * {@code common.event} record, picked up {@code @Async}/{@code AFTER_COMMIT} by
 * {@code ActivityEventListener}), so every assertion here polls with Awaitility rather than reading
 * the activity feed immediately after the triggering request returns.
 */
@AutoConfigureMockMvc
class IssueActivityControllerIT extends AbstractIntegrationTest {

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

    private IssueResponse createIssue(String projectKey, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null,
                                        null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse.class);
    }

    private UUID doneStatusIdOf(String projectKey, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectKey}/workflow", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        WorkflowSchemeResponse scheme = objectMapper.readValue(result.getResponse().getContentAsString(),
                WorkflowSchemeResponse.class);
        return scheme.statuses().stream().filter(s -> s.category() == StatusCategory.DONE).findFirst().orElseThrow()
                .id();
    }

    private List<ActivityEventResponse> fetchActivity(String issueKey, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/issues/{issueKey}/activity", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        ActivityEventResponse[] events = objectMapper.readValue(result.getResponse().getContentAsString(),
                ActivityEventResponse[].class);
        return List.of(events);
    }

    @Test
    void activityFeedRecordsCreateStatusChangeAndCommentInOrder() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse issue = createIssue(key, token);
        UUID doneStatusId = doneStatusIdOf(key, token);

        mockMvc.perform(patch("/api/v1/issues/{issueKey}/status", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeIssueStatusRequest(doneStatusId))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/issues/{issueKey}/comments", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("Looks good"))))
                .andExpect(status().isCreated());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<ActivityEventResponse> events = fetchActivity(issue.key(), token);
            assertThat(events).extracting(ActivityEventResponse::eventType).containsExactly(
                    ActivityEventTypes.ISSUE_CREATED, ActivityEventTypes.ISSUE_STATUS_CHANGED,
                    ActivityEventTypes.COMMENT_ADDED);
            assertThat(events).allMatch(e -> e.issueId().equals(issue.id()));
            assertThat(events.get(1).payload()).containsEntry("to_status", "Done");
            assertThat(events.get(2).payload()).containsEntry("excerpt", "Looks good");
        });
    }

    @Test
    void activityFeedIsEmptyForABrandNewIssueBeforeAnyFurtherChange() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse issue = createIssue(key, token);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<ActivityEventResponse> events = fetchActivity(issue.key(), token);
            assertThat(events).hasSize(1);
            assertThat(events.get(0).eventType()).isEqualTo(ActivityEventTypes.ISSUE_CREATED);
        });
    }

    @Test
    void activityFeedForUnknownIssueReturns404() throws Exception {
        String token = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/v1/issues/{issueKey}/activity", "NOPE-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void activityFeedForNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));
        IssueResponse issue = createIssue(key, ownerToken);

        String outsiderToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/v1/issues/{issueKey}/activity", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }
}
