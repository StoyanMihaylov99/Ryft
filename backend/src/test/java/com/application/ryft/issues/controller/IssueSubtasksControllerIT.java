package com.application.ryft.issues.controller;

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
import com.application.ryft.issues.dto.BoardResponse;
import com.application.ryft.issues.dto.ChangeIssueStatusRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.CreateSubtaskRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.projects.entity.Project;
import com.application.ryft.projects.entity.ProjectMember;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
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
class IssueSubtasksControllerIT extends AbstractIntegrationTest {

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

    private IssueResponse createIssueOfType(String projectKey, String token, IssueType type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(type, type + " title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse.class);
    }

    @Test
    void createSubtaskUnderStorySucceeds() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse story = createIssueOfType(key, token, IssueType.STORY);

        MvcResult result = mockMvc.perform(post("/api/v1/issues/{issueKey}/subtasks", story.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSubtaskRequest("Checklist item", null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse subtask = objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse.class);

        assertThat(subtask.type()).isEqualTo(IssueType.SUBTASK);
        assertThat(subtask.parentId()).isEqualTo(story.id());
    }

    @Test
    void createSubtaskByPlainMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));
        IssueResponse story = createIssueOfType(key, ownerToken, IssueType.STORY);

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        mockMvc.perform(post("/api/v1/issues/{issueKey}/subtasks", story.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSubtaskRequest("Checklist item", null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSubtaskUnderEpicReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse epic = createIssueOfType(key, token, IssueType.EPIC);

        mockMvc.perform(post("/api/v1/issues/{issueKey}/subtasks", epic.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSubtaskRequest("Checklist item", null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSubtaskUnderAnotherSubtaskReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse story = createIssueOfType(key, token, IssueType.STORY);
        MvcResult created = mockMvc.perform(post("/api/v1/issues/{issueKey}/subtasks", story.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSubtaskRequest("First-level subtask", null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse subtask = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        mockMvc.perform(post("/api/v1/issues/{issueKey}/subtasks", subtask.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSubtaskRequest("Nested subtask", null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSubtaskForUnknownParentReturns404() throws Exception {
        String token = registerAndGetToken(uniqueEmail());

        mockMvc.perform(post("/api/v1/issues/{issueKey}/subtasks", "NOPE-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSubtaskRequest("Checklist item", null, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listSubtasksReturnsChildrenInCreationOrder() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse story = createIssueOfType(key, token, IssueType.STORY);

        mockMvc.perform(post("/api/v1/issues/{issueKey}/subtasks", story.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSubtaskRequest("First", null, null, null))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/issues/{issueKey}/subtasks", story.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSubtaskRequest("Second", null, null, null))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/issues/{issueKey}/subtasks", story.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse[] subtasks = objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse[].class);
        assertThat(subtasks).extracting(IssueResponse::title).containsExactly("First", "Second");
    }

    @Test
    void listSubtasksIsVisibleToPlainMember() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));
        IssueResponse story = createIssueOfType(key, ownerToken, IssueType.STORY);

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        mockMvc.perform(get("/api/v1/issues/{issueKey}/subtasks", story.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isOk());
    }

    @Test
    void subtaskStatusCanBeChangedByAPlainMember() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));
        IssueResponse story = createIssueOfType(key, ownerToken, IssueType.STORY);
        MvcResult created = mockMvc.perform(post("/api/v1/issues/{issueKey}/subtasks", story.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSubtaskRequest("Checklist item", null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse subtask = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        MvcResult updated = mockMvc.perform(patch("/api/v1/issues/{issueKey}/status", subtask.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeIssueStatusRequest(IssueStatus.DONE))))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse result = objectMapper.readValue(updated.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(result.status()).isEqualTo(IssueStatus.DONE);
    }

    @Test
    void subtaskIsExcludedFromProjectIssuesListBacklogAndBoard() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse story = createIssueOfType(key, token, IssueType.STORY);
        mockMvc.perform(post("/api/v1/issues/{issueKey}/subtasks", story.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSubtaskRequest("Checklist item", null, null, null))))
                .andExpect(status().isCreated());

        MvcResult listResult = mockMvc.perform(get("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse[] issues = objectMapper.readValue(listResult.getResponse().getContentAsString(), IssueResponse[].class);
        assertThat(issues).extracting(IssueResponse::type).doesNotContain(IssueType.SUBTASK);

        MvcResult backlogResult = mockMvc.perform(get("/api/v1/projects/{projectKey}/backlog", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse[] backlogIssues = objectMapper.readValue(backlogResult.getResponse().getContentAsString(),
                IssueResponse[].class);
        assertThat(backlogIssues).extracting(IssueResponse::type).doesNotContain(IssueType.SUBTASK);

        MvcResult boardResult = mockMvc.perform(get("/api/v1/projects/{projectKey}/board", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        BoardResponse board = objectMapper.readValue(boardResult.getResponse().getContentAsString(), BoardResponse.class);
        assertThat(board.columns()).flatExtracting("issues").extracting("type").doesNotContain(IssueType.SUBTASK);
    }

    @Test
    void deletingParentCascadesDeletesItsSubtasks() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse story = createIssueOfType(key, token, IssueType.STORY);
        MvcResult created = mockMvc.perform(post("/api/v1/issues/{issueKey}/subtasks", story.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSubtaskRequest("Checklist item", null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse subtask = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        mockMvc.perform(delete("/api/v1/issues/{issueKey}", story.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/issues/{issueKey}", subtask.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAnEpicUnlinksLinkedIssuesInsteadOfDeletingThem() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        IssueResponse epic = createIssueOfType(key, token, IssueType.EPIC);
        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.STORY, "Linked story", null, null, null, null,
                                        epic.id(), null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse linkedStory = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);

        mockMvc.perform(delete("/api/v1/issues/{issueKey}", epic.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        MvcResult afterDelete = mockMvc.perform(get("/api/v1/issues/{issueKey}", linkedStory.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse result = objectMapper.readValue(afterDelete.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(result.parentId()).isNull();
    }
}
