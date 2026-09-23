package com.application.ryft.search.controller;

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
import com.application.ryft.issues.dto.ComponentResponse;
import com.application.ryft.issues.dto.CreateComponentRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.CreateLabelRequest;
import com.application.ryft.issues.dto.CreateSubtaskRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.LabelResponse;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.projects.entity.Project;
import com.application.ryft.projects.entity.ProjectMember;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
import com.application.ryft.search.dto.IssueSearchRequest;
import com.application.ryft.sprints.dto.CreateSprintRequest;
import com.application.ryft.sprints.dto.MoveIssueToSprintRequest;
import com.application.ryft.sprints.dto.SprintResponse;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
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
 * Covers {@code POST /projects/{projectKey}/search}, the AND-combining structured filter — see
 * {@code IssueLabelingControllerIT}/{@code BacklogControllerIT} for the single-field filters this
 * complements ({@code ProjectIssuesController}'s precedence chain, and moving issues into a sprint).
 */
@AutoConfigureMockMvc
class ProjectSearchControllerIT extends AbstractIntegrationTest {

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

    private LabelResponse createLabel(String projectKey, String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/labels", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLabelRequest(name, "#FF0000"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), LabelResponse.class);
    }

    private ComponentResponse createComponent(String projectKey, String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/components", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComponentRequest(name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ComponentResponse.class);
    }

    private SprintResponse createSprint(String projectKey, String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/sprints", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSprintRequest(name, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), SprintResponse.class);
    }

    private IssueResponse createIssue(String projectKey, String token, IssueType type, UUID assigneeId,
            List<UUID> labelIds, List<UUID> componentIds) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateIssueRequest(type, "Title " + type, null,
                                null, assigneeId, null, null, labelIds, componentIds))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse.class);
    }

    private IssueResponse createIssueWithTitleAndDescription(String projectKey, String token, String title,
            String description, List<UUID> labelIds) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateIssueRequest(IssueType.TASK, title,
                                description, null, null, null, null, labelIds, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse.class);
    }

    private void moveToSprint(String token, String issueKey, UUID sprintId) throws Exception {
        mockMvc.perform(patch("/api/v1/issues/{issueKey}/sprint", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveIssueToSprintRequest(sprintId))))
                .andExpect(status().isOk());
    }

    private void changeStatus(String token, String issueKey, UUID statusId) throws Exception {
        mockMvc.perform(patch("/api/v1/issues/{issueKey}/status", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeIssueStatusRequest(statusId))))
                .andExpect(status().isOk());
    }

    private WorkflowSchemeResponse getScheme(String projectKey, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectKey}/workflow", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), WorkflowSchemeResponse.class);
    }

    private IssueResponse[] search(String projectKey, String token, IssueSearchRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/search", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse[].class);
    }

    @Test
    void searchAndCombinesEveryPresentFieldAcrossAssigneeTypeLabelAndSprint() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        User owner = userOf(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, owner);

        String memberEmail = uniqueEmail();
        registerAndGetToken(memberEmail);
        User member = userOf(memberEmail);
        addMembership(project, member, ProjectRole.MEMBER);

        LabelResponse label = createLabel(key, ownerToken, "Backend");
        SprintResponse sprint = createSprint(key, ownerToken, "Sprint 1");

        IssueResponse matching = createIssue(key, ownerToken, IssueType.STORY, owner.getId(), List.of(label.id()), null);
        moveToSprint(ownerToken, matching.key(), sprint.id());

        // Near-misses: each differs from `matching` in exactly one filtered field.
        createIssue(key, ownerToken, IssueType.BUG, owner.getId(), List.of(label.id()), null); // wrong type
        IssueResponse wrongAssignee = createIssue(key, ownerToken, IssueType.STORY, member.getId(), List.of(label.id()), null);
        moveToSprint(ownerToken, wrongAssignee.key(), sprint.id());
        createIssue(key, ownerToken, IssueType.STORY, owner.getId(), null, null); // no label, no sprint

        IssueSearchRequest request = new IssueSearchRequest(List.of(owner.getId()), null, List.of(label.id()), null,
                List.of(IssueType.STORY), List.of(sprint.id()), null);
        IssueResponse[] results = search(key, ownerToken, request);

        assertThat(results).extracting(IssueResponse::key).containsExactly(matching.key());
    }

    @Test
    void searchWithMultipleValuesInOneFieldOrsThemTogether() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        User owner = userOf(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, owner);

        String memberEmail = uniqueEmail();
        registerAndGetToken(memberEmail);
        User member = userOf(memberEmail);
        addMembership(project, member, ProjectRole.MEMBER);

        String otherEmail = uniqueEmail();
        registerAndGetToken(otherEmail);
        User other = userOf(otherEmail);
        addMembership(project, other, ProjectRole.MEMBER);

        IssueResponse ownerIssue = createIssue(key, ownerToken, IssueType.TASK, owner.getId(), null, null);
        IssueResponse memberIssue = createIssue(key, ownerToken, IssueType.TASK, member.getId(), null, null);
        createIssue(key, ownerToken, IssueType.TASK, other.getId(), null, null);

        IssueSearchRequest request = new IssueSearchRequest(List.of(owner.getId(), member.getId()), null, null, null,
                null, null, null);
        IssueResponse[] results = search(key, ownerToken, request);

        assertThat(results).extracting(IssueResponse::key)
                .containsExactlyInAnyOrder(ownerIssue.key(), memberIssue.key());
    }

    @Test
    void searchWithMultipleLabelIdsOrsThemTogetherViaTheSubqueryPath() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        User owner = userOf(ownerEmail);
        String key = uniqueKey();
        createProject(key, owner);

        LabelResponse backend = createLabel(key, ownerToken, "Backend");
        LabelResponse frontend = createLabel(key, ownerToken, "Frontend");

        IssueResponse backendIssue = createIssue(key, ownerToken, IssueType.TASK, null, List.of(backend.id()), null);
        IssueResponse frontendIssue = createIssue(key, ownerToken, IssueType.TASK, null, List.of(frontend.id()), null);
        createIssue(key, ownerToken, IssueType.TASK, null, null, null); // no label at all

        IssueSearchRequest request = new IssueSearchRequest(null, null, List.of(backend.id(), frontend.id()), null,
                null, null, null);
        IssueResponse[] results = search(key, ownerToken, request);

        // Asserts no duplicate rows from the label-join subquery and correct OR-within-field semantics.
        assertThat(results).extracting(IssueResponse::key)
                .containsExactlyInAnyOrder(backendIssue.key(), frontendIssue.key());
    }

    @Test
    void searchWithMultipleComponentIdsOrsThemTogetherViaTheSubqueryPath() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        User owner = userOf(ownerEmail);
        String key = uniqueKey();
        createProject(key, owner);

        ComponentResponse api = createComponent(key, ownerToken, "API");
        ComponentResponse ui = createComponent(key, ownerToken, "UI");

        IssueResponse apiIssue = createIssue(key, ownerToken, IssueType.TASK, null, null, List.of(api.id()));
        IssueResponse uiIssue = createIssue(key, ownerToken, IssueType.TASK, null, null, List.of(ui.id()));
        createIssue(key, ownerToken, IssueType.TASK, null, null, null); // no component at all

        IssueSearchRequest request = new IssueSearchRequest(null, null, null, List.of(api.id(), ui.id()), null, null, null);
        IssueResponse[] results = search(key, ownerToken, request);

        // Asserts no duplicate rows from the component-join subquery and correct OR-within-field semantics.
        assertThat(results).extracting(IssueResponse::key)
                .containsExactlyInAnyOrder(apiIssue.key(), uiIssue.key());
    }

    @Test
    void searchFiltersByStatusIdAndComponentId() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        User owner = userOf(ownerEmail);
        String key = uniqueKey();
        createProject(key, owner);

        ComponentResponse component = createComponent(key, ownerToken, "API");
        WorkflowSchemeResponse scheme = getScheme(key, ownerToken);
        UUID inProgressStatusId = scheme.statuses().stream()
                .filter(status -> status.name().equals("In Progress"))
                .map(WorkflowStatusResponse::id)
                .findFirst()
                .orElseThrow();

        IssueResponse matching = createIssue(key, ownerToken, IssueType.TASK, null, null, List.of(component.id()));
        changeStatus(ownerToken, matching.key(), inProgressStatusId);
        createIssue(key, ownerToken, IssueType.TASK, null, null, List.of(component.id())); // stays To Do
        createIssue(key, ownerToken, IssueType.TASK, null, null, null); // no component at all

        IssueSearchRequest request = new IssueSearchRequest(null, List.of(inProgressStatusId), null,
                List.of(component.id()), null, null, null);
        IssueResponse[] results = search(key, ownerToken, request);

        assertThat(results).extracting(IssueResponse::key).containsExactly(matching.key());
    }

    private IssueSearchRequest textRequest(String text) {
        return new IssueSearchRequest(null, null, null, null, null, null, text);
    }

    @Test
    void searchByTextMatchesTitle() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        IssueResponse matching = createIssueWithTitleAndDescription(key, ownerToken, "Fix login timeout", null, null);
        createIssueWithTitleAndDescription(key, ownerToken, "Update dashboard layout", null, null);

        IssueResponse[] results = search(key, ownerToken, textRequest("login"));

        assertThat(results).extracting(IssueResponse::key).containsExactly(matching.key());
    }

    @Test
    void searchByTextMatchesDescription() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        IssueResponse matching = createIssueWithTitleAndDescription(key, ownerToken, "Unrelated title",
                "Users are getting logged out unexpectedly", null);
        createIssueWithTitleAndDescription(key, ownerToken, "Another issue", "Nothing relevant here", null);

        IssueResponse[] results = search(key, ownerToken, textRequest("logged out"));

        assertThat(results).extracting(IssueResponse::key).containsExactly(matching.key());
    }

    @Test
    void searchByTextWithNoMatchesReturnsEmptyNotEverything() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        createIssueWithTitleAndDescription(key, ownerToken, "Fix login timeout", null, null);

        IssueResponse[] results = search(key, ownerToken, textRequest("nonexistentxyzterm"));

        assertThat(results).isEmpty();
    }

    @Test
    void searchByTextIsCaseInsensitive() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        IssueResponse matching = createIssueWithTitleAndDescription(key, ownerToken, "Fix Login Timeout", null, null);

        IssueResponse[] results = search(key, ownerToken, textRequest("LOGIN"));

        assertThat(results).extracting(IssueResponse::key).containsExactly(matching.key());
    }

    @Test
    void searchByTextWithMultipleWordsMatchesAcrossTitleAndDescription() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        IssueResponse matching = createIssueWithTitleAndDescription(key, ownerToken, "Fix login",
                "timeout occurs after five minutes", null);
        createIssueWithTitleAndDescription(key, ownerToken, "Fix login", "works fine now", null);

        IssueResponse[] results = search(key, ownerToken, textRequest("login timeout"));

        assertThat(results).extracting(IssueResponse::key).containsExactly(matching.key());
    }

    @Test
    void searchByTextCombinedWithLabelIdAndsThemTogether() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        LabelResponse label = createLabel(key, ownerToken, "Backend");
        IssueResponse matching = createIssueWithTitleAndDescription(key, ownerToken, "Fix login timeout", null,
                List.of(label.id()));
        // Same text match, but missing the label — must be excluded by the AND.
        createIssueWithTitleAndDescription(key, ownerToken, "Fix login timeout", null, null);
        // Has the label, but non-matching text — must also be excluded by the AND. Without this issue,
        // a regression that silently drops the text filter (leaving only the label filter running)
        // would still pass this test, since the label-only result set happens to coincide with the
        // correct one otherwise.
        createIssueWithTitleAndDescription(key, ownerToken, "Update dashboard layout", null, List.of(label.id()));

        IssueSearchRequest request = new IssueSearchRequest(null, null, List.of(label.id()), null, null, null,
                "login");
        IssueResponse[] results = search(key, ownerToken, request);

        assertThat(results).extracting(IssueResponse::key).containsExactly(matching.key());
    }

    /**
     * {@code plainto_tsquery} ANDs together every lexeme it extracts from the input, ignoring characters
     * that aren't part of a word (like {@code &}/{@code |}/{@code !}) rather than treating them as tsquery
     * operators — so this only matches an issue containing every one of those lexemes, not a syntax error.
     * That's the behavior under test: a raw {@code to_tsquery} call with this same input would throw a
     * Postgres syntax error (mismatched operators) instead of returning a plain, safe match.
     */
    @Test
    void searchByTextWithTsquerySpecialCharactersDoesNotThrow() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        IssueResponse matching = createIssueWithTitleAndDescription(key, ownerToken,
                "Fix user's login & session urgent issue", null, null);

        IssueResponse[] results = search(key, ownerToken, textRequest("user's login & session | urgent!"));

        assertThat(results).extracting(IssueResponse::key).containsExactly(matching.key());
    }

    @Test
    void searchByTextExceedingMaxLengthReturns400() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        mockMvc.perform(post("/api/v1/projects/{projectKey}/search", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(textRequest("x".repeat(201)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchWithNoFiltersReturnsEverythingExceptSubtasks() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        IssueResponse parent = createIssue(key, token, IssueType.STORY, null, null, null);
        createIssue(key, token, IssueType.TASK, null, null, null);
        mockMvc.perform(post("/api/v1/issues/{issueKey}/subtasks", parent.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSubtaskRequest("Checklist item", null, null, null))))
                .andExpect(status().isCreated());

        IssueResponse[] results = search(key, token, new IssueSearchRequest(null, null, null, null, null, null, null));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(IssueResponse::type).doesNotContain(IssueType.SUBTASK);
    }

    @Test
    void searchByNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        String outsiderToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(post("/api/v1/projects/{projectKey}/search", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssueSearchRequest(null, null, null, null, null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchForUnknownProjectReturns404() throws Exception {
        String token = registerAndGetToken(uniqueEmail());

        mockMvc.perform(post("/api/v1/projects/{projectKey}/search", uniqueKey())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssueSearchRequest(null, null, null, null, null, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchIsVisibleToPlainMember() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        mockMvc.perform(post("/api/v1/projects/{projectKey}/search", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssueSearchRequest(null, null, null, null, null, null, null))))
                .andExpect(status().isOk());
    }

    /** Matches {@code IssueService.search}'s javadoc claim that a Viewer may call it too, unlike the
     *  write endpoints this module's {@code requireNotViewer}/{@code requireOwnerOrAdmin} gates block. */
    @Test
    void searchIsVisibleToViewer() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String viewerEmail = uniqueEmail();
        String viewerToken = registerAndGetToken(viewerEmail);
        addMembership(project, userOf(viewerEmail), ProjectRole.VIEWER);

        mockMvc.perform(post("/api/v1/projects/{projectKey}/search", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssueSearchRequest(null, null, null, null, null, null, null))))
                .andExpect(status().isOk());
    }
}
