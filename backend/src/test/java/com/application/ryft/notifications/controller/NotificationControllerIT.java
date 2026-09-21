package com.application.ryft.notifications.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.notifications.dto.NotificationResponse;
import com.application.ryft.notifications.entity.NotificationType;
import com.application.ryft.projects.dto.AddProjectMemberRequest;
import com.application.ryft.projects.entity.Project;
import com.application.ryft.projects.entity.ProjectMember;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
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
 * Notifications are created asynchronously (the event listener runs {@code @Async} + {@code
 * AFTER_COMMIT}), so every test that expects one to exist polls with Awaitility instead of asserting
 * immediately after the triggering request returns.
 */
@AutoConfigureMockMvc
class NotificationControllerIT extends AbstractIntegrationTest {

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

    private void addMemberByEmail(String projectKey, String ownerToken, String memberEmail) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectKey}/members", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddProjectMemberRequest(memberEmail,
                                ProjectRole.MEMBER))))
                .andExpect(status().isCreated());
    }

    private List<NotificationResponse> listNotifications(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        NotificationResponse[] notifications = objectMapper.readValue(result.getResponse().getContentAsString(),
                NotificationResponse[].class);
        return List.of(notifications);
    }

    @Test
    void assignedNotificationEventuallyAppearsForTheAssigneeWithIssueKeyAndActorDisplayName() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        String assigneeEmail = uniqueEmail();
        String assigneeToken = registerAndGetToken(assigneeEmail);
        addMemberByEmail(key, ownerToken, assigneeEmail);
        UUID assigneeId = userOf(assigneeEmail).getId();

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Assigned at creation", null, null,
                                        assigneeId, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<NotificationResponse> notifications = listNotifications(assigneeToken);
            assertThat(notifications).hasSize(1);
            NotificationResponse notification = notifications.get(0);
            assertThat(notification.type()).isEqualTo(NotificationType.ASSIGNED);
            assertThat(notification.readAt()).isNull();
            assertThat(notification.issueKey()).isEqualTo(issue.key());
            assertThat(notification.actorDisplayName()).isEqualTo(userOf(ownerEmail).getDisplayName());
        });
    }

    @Test
    void statusChangedCommentAndMentionNotificationsCarryIssueKeyAndActorDisplayName() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String ownerDisplayName = userOf(ownerEmail).getDisplayName();
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        String assigneeEmail = uniqueEmail();
        String assigneeToken = registerAndGetToken(assigneeEmail);
        addMemberByEmail(key, ownerToken, assigneeEmail);
        UUID assigneeId = userOf(assigneeEmail).getId();

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, assigneeId, null, null,
                                        null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        // Wait for the "assigned at creation" notification first, so the later assertions can rely on
        // the status-changed/comment/mention notifications being exactly the newly-appeared ones.
        await().atMost(Duration.ofSeconds(10)).until(() -> listNotifications(assigneeToken).size() == 1);

        UUID doneStatusId = doneStatusIdOf(key, ownerToken);
        mockMvc.perform(patch("/api/v1/issues/{issueKey}/status", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.application.ryft.issues.dto.ChangeIssueStatusRequest(doneStatusId))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/issues/{issueKey}/comments", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.application.ryft.issues.dto.CreateCommentRequest(
                                "cc @" + assigneeEmail))))
                .andExpect(status().isCreated());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<NotificationResponse> notifications = listNotifications(assigneeToken);
            // assigned (at creation) + status_changed + comment + mention
            assertThat(notifications).hasSize(4);
            assertThat(notifications).allMatch(n -> issue.key().equals(n.issueKey()));
            assertThat(notifications).allMatch(n -> ownerDisplayName.equals(n.actorDisplayName()));
            assertThat(notifications).extracting(NotificationResponse::type).containsExactlyInAnyOrder(
                    NotificationType.ASSIGNED, NotificationType.STATUS_CHANGED, NotificationType.COMMENT,
                    NotificationType.MENTION);
        });
    }

    private UUID doneStatusIdOf(String projectKey, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectKey}/workflow", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        com.application.ryft.workflow.dto.WorkflowSchemeResponse scheme = objectMapper.readValue(
                result.getResponse().getContentAsString(), com.application.ryft.workflow.dto.WorkflowSchemeResponse.class);
        return scheme.statuses().stream()
                .filter(s -> s.category() == com.application.ryft.workflow.entity.StatusCategory.DONE)
                .findFirst().orElseThrow().id();
    }

    @Test
    void markReadOnAnotherUsersNotificationReturns404() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        String assigneeEmail = uniqueEmail();
        String assigneeToken = registerAndGetToken(assigneeEmail);
        addMemberByEmail(key, ownerToken, assigneeEmail);
        UUID assigneeId = userOf(assigneeEmail).getId();

        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, assigneeId, null, null,
                                        null, null))))
                .andExpect(status().isCreated());

        UUID notificationId = await().atMost(Duration.ofSeconds(10)).until(
                () -> listNotifications(assigneeToken).stream().findFirst().map(NotificationResponse::id)
                        .orElse(null),
                id -> id != null);

        String outsiderToken = registerAndGetToken(uniqueEmail());
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void markReadByOwningUserSucceeds() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        String assigneeEmail = uniqueEmail();
        String assigneeToken = registerAndGetToken(assigneeEmail);
        addMemberByEmail(key, ownerToken, assigneeEmail);
        UUID assigneeId = userOf(assigneeEmail).getId();

        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, assigneeId, null, null,
                                        null, null))))
                .andExpect(status().isCreated());

        UUID notificationId = await().atMost(Duration.ofSeconds(10)).until(
                () -> listNotifications(assigneeToken).stream().findFirst().map(NotificationResponse::id)
                        .orElse(null),
                id -> id != null);

        MvcResult result = mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + assigneeToken))
                .andExpect(status().isOk())
                .andReturn();
        NotificationResponse updated = objectMapper.readValue(result.getResponse().getContentAsString(),
                NotificationResponse.class);
        assertThat(updated.readAt()).isNotNull();
    }

    @Test
    void markAllReadClearsEveryUnreadNotificationForTheCaller() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        String assigneeEmail = uniqueEmail();
        String assigneeToken = registerAndGetToken(assigneeEmail);
        addMemberByEmail(key, ownerToken, assigneeEmail);
        UUID assigneeId = userOf(assigneeEmail).getId();

        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, assigneeId, null, null,
                                        null, null))))
                .andExpect(status().isCreated());

        await().atMost(Duration.ofSeconds(10)).until(() -> !listNotifications(assigneeToken).isEmpty());

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + assigneeToken))
                .andExpect(status().isNoContent());

        List<NotificationResponse> notifications = listNotifications(assigneeToken);
        assertThat(notifications).isNotEmpty();
        assertThat(notifications).allMatch(n -> n.readAt() != null);
    }

    @Test
    void listWithoutAuthorizationHeaderReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void markAllReadWithoutAuthorizationHeaderReturns401() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
    }
}
