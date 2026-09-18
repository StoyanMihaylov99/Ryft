package com.application.ryft.issues.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.application.ryft.AbstractIntegrationTest;
import com.application.ryft.identity.auth.dto.RegisterRequest;
import com.application.ryft.identity.user.entity.User;
import com.application.ryft.identity.user.repository.UserRepository;
import com.application.ryft.identity.workspace.entity.Workspace;
import com.application.ryft.identity.workspace.repository.WorkspaceRepository;
import com.application.ryft.issues.dto.ChangeIssueStatusRequest;
import com.application.ryft.issues.dto.CreateCommentRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.CreateSubtaskRequest;
import com.application.ryft.issues.dto.EpicProgressResponse;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.ReorderBacklogIssueRequest;
import com.application.ryft.issues.dto.UpdateIssueRequest;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.projects.entity.Project;
import com.application.ryft.projects.entity.ProjectMember;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
import com.application.ryft.sprints.dto.CreateSprintRequest;
import com.application.ryft.sprints.dto.MoveIssueToSprintRequest;
import com.application.ryft.sprints.dto.SprintResponse;
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
class IssueControllerIT extends AbstractIntegrationTest {

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
    void createIssueGeneratesSequentialKeys() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        MvcResult first = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.BUG, "First bug", "desc", null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse firstIssue = objectMapper.readValue(first.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(firstIssue.key()).isEqualTo(key + "-1");
        assertThat(firstIssue.status()).isEqualTo(IssueStatus.TODO);
        assertThat(firstIssue.priority()).isEqualTo(IssuePriority.MEDIUM);

        MvcResult second = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Second task", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse secondIssue = objectMapper.readValue(second.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(secondIssue.key()).isEqualTo(key + "-2");
    }

    @Test
    void createIssueByNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        String outsiderEmail = uniqueEmail();
        String outsiderToken = registerAndGetToken(outsiderEmail);

        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createIssueByPlainMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createIssueByAdminSucceeds() throws Exception {
        String ownerEmail = uniqueEmail();
        registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String adminEmail = uniqueEmail();
        String adminToken = registerAndGetToken(adminEmail);
        addMembership(project, userOf(adminEmail), ProjectRole.ADMIN);

        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated());
    }

    @Test
    void createIssueForUnknownProjectReturns404() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);

        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", uniqueKey())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void createIssueWithStoryPointsReflectsValueInResponse() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, 5, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(issue.storyPoints()).isEqualTo(5);
    }

    @Test
    void createIssueWithNonMemberAssigneeReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, UUID.randomUUID(), null, null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEpicIssueSucceeds() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.EPIC, "Epic title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse epic = objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(epic.type()).isEqualTo(IssueType.EPIC);
        assertThat(epic.parentId()).isNull();
    }

    @Test
    void createStoryLinkedToEpicSucceeds() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse epic = createIssueOfType(key, token, IssueType.EPIC, "Epic title", null);

        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.STORY, "Story title", null, null, null, null,
                                        epic.id(), null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse story = objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(story.parentId()).isEqualTo(epic.id());
    }

    @Test
    void createIssueWithParentNotAnEpicReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse otherStory = createIssueOfType(key, token, IssueType.STORY, "Not an epic", null);

        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Task title", null, null, null, null,
                                        otherStory.id(), null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createIssueWithUnknownParentReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Task title", null, null, null, null,
                                        UUID.randomUUID(), null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEpicWithParentReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse epic = createIssueOfType(key, token, IssueType.EPIC, "Epic title", null);

        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.EPIC, "Another epic", null, null, null, null,
                                        epic.id(), null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFilteredByEpicIdReturnsOnlyMatchingIssues() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse epic = createIssueOfType(key, token, IssueType.EPIC, "Epic title", null);
        IssueResponse linkedStory = createIssueOfType(key, token, IssueType.STORY, "Linked story", epic.id());
        createIssueOfType(key, token, IssueType.STORY, "Unlinked story", null);

        MvcResult filtered = mockMvc.perform(get("/api/v1/projects/{projectKey}/issues", key)
                        .param("epicId", epic.id().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse[] issues = objectMapper.readValue(filtered.getResponse().getContentAsString(), IssueResponse[].class);
        assertThat(issues).extracting(IssueResponse::key).containsExactly(linkedStory.key());
        assertThat(issues[0].parentId()).isEqualTo(epic.id());
    }

    private IssueResponse createIssueOfType(String projectKey, String token, IssueType type, String title,
            UUID parentId) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(type, title, null, null, null, null, parentId, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);
    }

    @Test
    void listReturnsIssuesForProject() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.STORY, "Story", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse[] issues = objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse[].class);
        assertThat(issues).hasSize(1);
        assertThat(issues[0].key()).isEqualTo(key + "-1");
    }

    @Test
    void listFilteredBySprintReturnsOnlyMatchingIssues() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        MvcResult backlogIssueResult = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Backlog issue", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse backlogIssue = objectMapper.readValue(backlogIssueResult.getResponse().getContentAsString(),
                IssueResponse.class);

        MvcResult sprintIssueResult = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Sprint issue", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse sprintIssue = objectMapper.readValue(sprintIssueResult.getResponse().getContentAsString(),
                IssueResponse.class);

        MvcResult sprintResult = mockMvc.perform(post("/api/v1/projects/{projectKey}/sprints", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSprintRequest("Sprint 1", null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        SprintResponse sprint = objectMapper.readValue(sprintResult.getResponse().getContentAsString(), SprintResponse.class);

        mockMvc.perform(patch("/api/v1/issues/{issueKey}/sprint", sprintIssue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveIssueToSprintRequest(sprint.id()))))
                .andExpect(status().isOk());

        MvcResult filtered = mockMvc.perform(get("/api/v1/projects/{projectKey}/issues", key)
                        .param("sprintId", sprint.id().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse[] issues = objectMapper.readValue(filtered.getResponse().getContentAsString(), IssueResponse[].class);
        assertThat(issues).extracting(IssueResponse::key).containsExactly(sprintIssue.key());
        assertThat(issues[0].sprintId()).isEqualTo(sprint.id());
        assertThat(backlogIssue.sprintId()).isNull();
    }

    @Test
    void reorderBacklogRankMovesIssueBetweenNeighbors() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        IssueResponse first = createIssue(key, token, "First");
        IssueResponse second = createIssue(key, token, "Second");
        IssueResponse third = createIssue(key, token, "Third");

        MvcResult reordered = mockMvc.perform(patch("/api/v1/issues/{issueKey}/backlog-rank", third.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReorderBacklogIssueRequest(first.key(), second.key()))))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse result = objectMapper.readValue(reordered.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(result.key()).isEqualTo(third.key());

        MvcResult backlog = mockMvc.perform(get("/api/v1/projects/{projectKey}/backlog", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse[] issues = objectMapper.readValue(backlog.getResponse().getContentAsString(), IssueResponse[].class);
        assertThat(issues).extracting(IssueResponse::key).containsExactly(first.key(), third.key(), second.key());
    }

    @Test
    void reorderBacklogRankByPlainMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));
        IssueResponse issue = createIssue(key, ownerToken, "Title");

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        mockMvc.perform(patch("/api/v1/issues/{issueKey}/backlog-rank", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReorderBacklogIssueRequest(null, null))))
                .andExpect(status().isForbidden());
    }

    private IssueResponse createIssue(String projectKey, String token, String title) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, title, null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);
    }

    @Test
    void getByKeyIsCaseInsensitive() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        mockMvc.perform(get("/api/v1/issues/{issueKey}", issue.key().toLowerCase())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void getOfUnknownIssueReturns404() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);

        mockMvc.perform(get("/api/v1/issues/{issueKey}", "NOPE-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateChangesTitleAndAssignee() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String memberEmail = uniqueEmail();
        registerAndGetToken(memberEmail);
        User member = userOf(memberEmail);
        addMembership(project, member, ProjectRole.MEMBER);

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        MvcResult updated = mockMvc.perform(patch("/api/v1/issues/{issueKey}", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateIssueRequest("New title", null, null, member.getId(), null, null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse result = objectMapper.readValue(updated.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(result.title()).isEqualTo("New title");
        assertThat(result.assigneeId()).isEqualTo(member.getId());
    }

    @Test
    void updateChangesStoryPoints() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        MvcResult updated = mockMvc.perform(patch("/api/v1/issues/{issueKey}", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateIssueRequest(null, null, null, null, 8, null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse result = objectMapper.readValue(updated.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(result.storyPoints()).isEqualTo(8);
    }

    @Test
    void updateWithParentIdEqualToOwnIdReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse issue = createIssueOfType(key, token, IssueType.STORY, "Story title", null);

        // Self-link is unreachable via create: Issue.id is assigned by Hibernate's @UuidGenerator only on
        // persist, so a not-yet-saved issue has no id for request.parentId() to match against.
        mockMvc.perform(patch("/api/v1/issues/{issueKey}", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateIssueRequest(null, null, null, null, null, issue.id(), null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An issue cannot be linked to itself as its parent"));
    }

    @Test
    void updateByPlainMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        mockMvc.perform(patch("/api/v1/issues/{issueKey}", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateIssueRequest("Hijacked", null, null, null, null, null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatusToDoneSetsResolvedAt() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.BUG, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        MvcResult updated = mockMvc.perform(patch("/api/v1/issues/{issueKey}/status", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeIssueStatusRequest(IssueStatus.DONE))))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse result = objectMapper.readValue(updated.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(result.status()).isEqualTo(IssueStatus.DONE);
        assertThat(result.resolvedAt()).isNotNull();
    }

    @Test
    void updateStatusByPlainMemberSucceeds() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        // Unlike create/update/delete, a plain Member is allowed to drag a card (change its status).
        mockMvc.perform(patch("/api/v1/issues/{issueKey}/status", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeIssueStatusRequest(IssueStatus.IN_PROGRESS))))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatusByNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.BUG, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        String outsiderToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(patch("/api/v1/issues/{issueKey}/status", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeIssueStatusRequest(IssueStatus.DONE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteByPlainMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        mockMvc.perform(delete("/api/v1/issues/{issueKey}", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRemovesIssue() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        mockMvc.perform(delete("/api/v1/issues/{issueKey}", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/issues/{issueKey}", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesIssueWithExistingComments() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        mockMvc.perform(post("/api/v1/issues/{issueKey}/comments", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("A comment"))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/issues/{issueKey}", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/issues/{issueKey}", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void epicProgressWithMixOfDoneAndNotDoneLinkedIssuesComputesPercent() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse epic = createIssueOfType(key, token, IssueType.EPIC, "Epic title", null);
        IssueResponse done = createIssueOfType(key, token, IssueType.STORY, "Done story", epic.id());
        createIssueOfType(key, token, IssueType.TASK, "Not done task", epic.id());
        createIssueOfType(key, token, IssueType.BUG, "Not done bug", epic.id());
        createIssueOfType(key, token, IssueType.STORY, "Unrelated story", null);

        mockMvc.perform(patch("/api/v1/issues/{issueKey}/status", done.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeIssueStatusRequest(IssueStatus.DONE))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/issues/{issueKey}/progress", epic.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        EpicProgressResponse progress = objectMapper.readValue(result.getResponse().getContentAsString(),
                EpicProgressResponse.class);
        assertThat(progress.totalCount()).isEqualTo(3);
        assertThat(progress.doneCount()).isEqualTo(1);
        assertThat(progress.percentDone()).isCloseTo(33.33, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void epicProgressWithNoLinkedIssuesReturnsZeroPercent() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse epic = createIssueOfType(key, token, IssueType.EPIC, "Empty epic", null);

        MvcResult result = mockMvc.perform(get("/api/v1/issues/{issueKey}/progress", epic.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        EpicProgressResponse progress = objectMapper.readValue(result.getResponse().getContentAsString(),
                EpicProgressResponse.class);
        assertThat(progress).isEqualTo(new EpicProgressResponse(0, 0, 0.0));
    }

    @Test
    void epicProgressOnNonEpicIssueReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse story = createIssueOfType(key, token, IssueType.STORY, "Not an epic", null);

        mockMvc.perform(get("/api/v1/issues/{issueKey}/progress", story.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void epicProgressOfUnknownKeyReturns404() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);

        mockMvc.perform(get("/api/v1/issues/{issueKey}/progress", "NOPE-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void epicProgressDoesNotCountSubtasksOfALinkedStory() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse epic = createIssueOfType(key, token, IssueType.EPIC, "Epic title", null);
        IssueResponse story = createIssueOfType(key, token, IssueType.STORY, "Linked story", epic.id());

        MvcResult subtaskResult = mockMvc.perform(post("/api/v1/issues/{issueKey}/subtasks", story.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSubtaskRequest("Checklist item", null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse subtask = objectMapper.readValue(subtaskResult.getResponse().getContentAsString(),
                IssueResponse.class);
        mockMvc.perform(patch("/api/v1/issues/{issueKey}/status", subtask.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeIssueStatusRequest(IssueStatus.DONE))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/issues/{issueKey}/progress", epic.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        EpicProgressResponse progress = objectMapper.readValue(result.getResponse().getContentAsString(),
                EpicProgressResponse.class);
        // The subtask being DONE must not count toward the epic's progress — only its parent story does,
        // and that story is still not DONE.
        assertThat(progress.totalCount()).isEqualTo(1);
        assertThat(progress.doneCount()).isEqualTo(0);
        assertThat(progress.percentDone()).isEqualTo(0.0);
    }
}
