package com.application.ryft.workflow.controller;

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
import com.application.ryft.workflow.dto.UpdateWorkflowSchemeRequest;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusEdit;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.dto.WorkflowTransitionEdit;
import com.application.ryft.workflow.entity.StatusCategory;
import com.application.ryft.workflow.entity.WorkflowScheme;
import com.application.ryft.workflow.entity.WorkflowStatus;
import com.application.ryft.workflow.repository.WorkflowSchemeRepository;
import com.application.ryft.workflow.repository.WorkflowStatusRepository;
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
class WorkflowControllerIT extends AbstractIntegrationTest {

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
    private WorkflowSchemeRepository workflowSchemeRepository;

    @Autowired
    private WorkflowStatusRepository workflowStatusRepository;

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

    private WorkflowSchemeResponse getScheme(String projectKey, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectKey}/workflow", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), WorkflowSchemeResponse.class);
    }

    private List<WorkflowStatusEdit> statusEditsPreserving(WorkflowSchemeResponse scheme) {
        return scheme.statuses().stream()
                .map(s -> new WorkflowStatusEdit(s.id(), s.name(), s.category(), s.sortOrder()))
                .toList();
    }

    @Test
    void getCreatesDefaultSchemeOnFirstRequest() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectKey}/workflow", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        WorkflowSchemeResponse scheme = objectMapper.readValue(result.getResponse().getContentAsString(),
                WorkflowSchemeResponse.class);
        assertThat(scheme.statuses()).hasSize(4);
        assertThat(scheme.statuses().get(0).name()).isEqualTo("To Do");
        assertThat(scheme.statuses().get(0).category()).isEqualTo(StatusCategory.TODO);
        assertThat(scheme.statuses().get(1).name()).isEqualTo("Blocked");
        assertThat(scheme.statuses().get(1).category()).isEqualTo(StatusCategory.BLOCKED);
        assertThat(scheme.statuses().get(2).name()).isEqualTo("In Progress");
        assertThat(scheme.statuses().get(2).category()).isEqualTo(StatusCategory.IN_PROGRESS);
        assertThat(scheme.statuses().get(3).name()).isEqualTo("Done");
        assertThat(scheme.statuses().get(3).category()).isEqualTo(StatusCategory.DONE);
    }

    @Test
    void secondGetReturnsTheSameSchemeNotADuplicate() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));

        MvcResult first = mockMvc.perform(get("/api/v1/projects/{projectKey}/workflow", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(get("/api/v1/projects/{projectKey}/workflow", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        WorkflowSchemeResponse firstScheme = objectMapper.readValue(first.getResponse().getContentAsString(),
                WorkflowSchemeResponse.class);
        WorkflowSchemeResponse secondScheme = objectMapper.readValue(second.getResponse().getContentAsString(),
                WorkflowSchemeResponse.class);
        assertThat(secondScheme.id()).isEqualTo(firstScheme.id());
        assertThat(secondScheme.statuses()).hasSize(4);
    }

    @Test
    void getByNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        String outsiderToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/v1/projects/{projectKey}/workflow", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOfUnknownProjectReturns404() throws Exception {
        String token = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/v1/projects/{projectKey}/workflow", uniqueKey())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateByOwnerRenamesAStatus() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        WorkflowSchemeResponse scheme = getScheme(key, token);

        List<WorkflowStatusEdit> statusEdits = scheme.statuses().stream()
                .map(s -> s.category() == StatusCategory.TODO
                        ? new WorkflowStatusEdit(s.id(), "Backlog", s.category(), s.sortOrder())
                        : new WorkflowStatusEdit(s.id(), s.name(), s.category(), s.sortOrder()))
                .toList();

        MvcResult result = mockMvc.perform(patch("/api/v1/projects/{projectKey}/workflow", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateWorkflowSchemeRequest(statusEdits, List.of()))))
                .andExpect(status().isOk())
                .andReturn();
        WorkflowSchemeResponse updated = objectMapper.readValue(result.getResponse().getContentAsString(),
                WorkflowSchemeResponse.class);
        assertThat(updated.statuses()).extracting(WorkflowStatusResponse::name).contains("Backlog");
    }

    @Test
    void updateByMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));
        WorkflowSchemeResponse scheme = getScheme(key, ownerToken);

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        mockMvc.perform(patch("/api/v1/projects/{projectKey}/workflow", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateWorkflowSchemeRequest(statusEditsPreserving(scheme), List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateByViewerReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));
        WorkflowSchemeResponse scheme = getScheme(key, ownerToken);

        String viewerEmail = uniqueEmail();
        String viewerToken = registerAndGetToken(viewerEmail);
        addMembership(project, userOf(viewerEmail), ProjectRole.VIEWER);

        mockMvc.perform(patch("/api/v1/projects/{projectKey}/workflow", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateWorkflowSchemeRequest(statusEditsPreserving(scheme), List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateWithTransitionReferencingUnknownStatusReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        WorkflowSchemeResponse scheme = getScheme(key, token);

        mockMvc.perform(patch("/api/v1/projects/{projectKey}/workflow", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateWorkflowSchemeRequest(
                                statusEditsPreserving(scheme),
                                List.of(new WorkflowTransitionEdit(null, UUID.randomUUID(), UUID.randomUUID(), "Go"))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingAStatusStillReferencedByAnIssueReturns409() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        WorkflowSchemeResponse scheme = getScheme(key, token);

        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated());

        // Every fresh issue starts in the lowest-sortOrder status ("To Do") — omit it from the submitted
        // list to request its deletion while an issue still references it.
        List<WorkflowStatusEdit> statusEditsWithoutTodo = scheme.statuses().stream()
                .filter(s -> s.category() != StatusCategory.TODO)
                .map(s -> new WorkflowStatusEdit(s.id(), s.name(), s.category(), s.sortOrder()))
                .toList();

        mockMvc.perform(patch("/api/v1/projects/{projectKey}/workflow", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateWorkflowSchemeRequest(statusEditsWithoutTodo, List.of()))))
                .andExpect(status().isConflict());
    }

    /**
     * The default scheme already seeds a full any-to-any graph — submitting the existing transitions
     * unchanged plus a brand-new create request for a pair that's already present is the duplicate case.
     */
    @Test
    void addingADuplicateTransitionReturns409() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        WorkflowSchemeResponse scheme = getScheme(key, token);
        UUID todoId = scheme.statuses().stream().filter(s -> s.category() == StatusCategory.TODO).findFirst()
                .orElseThrow().id();
        UUID doneId = scheme.statuses().stream().filter(s -> s.category() == StatusCategory.DONE).findFirst()
                .orElseThrow().id();

        List<WorkflowTransitionEdit> preservedPlusDuplicate = new java.util.ArrayList<>(scheme.transitions().stream()
                .map(t -> new WorkflowTransitionEdit(t.id(), t.fromStatusId(), t.toStatusId(), t.name()))
                .toList());
        preservedPlusDuplicate.add(new WorkflowTransitionEdit(null, todoId, doneId, "Duplicate of the seeded any-to-any"));

        mockMvc.perform(patch("/api/v1/projects/{projectKey}/workflow", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateWorkflowSchemeRequest(statusEditsPreserving(scheme), preservedPlusDuplicate))))
                .andExpect(status().isConflict());
    }

    /**
     * Regression for the "transitions empty" sentinel being ambiguous with a deliberate empty set: an
     * Owner/Admin submitting {@code transitions: []} via {@code PATCH} is a deliberate lockdown, not "not
     * yet seeded" — the very next {@code GET} (and any status-change attempt) must not mistake the
     * resulting zero rows for a never-seeded scheme and silently re-seed any-to-any over that choice.
     */
    @Test
    void explicitlyEmptyTransitionsAreNotSilentlyReseededOnTheNextRead() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        WorkflowSchemeResponse scheme = getScheme(key, token);
        assertThat(scheme.transitions()).isNotEmpty();
        UUID todoStatusId = scheme.statuses().stream().filter(s -> s.category() == StatusCategory.TODO).findFirst()
                .orElseThrow().id();
        UUID doneStatusId = scheme.statuses().stream().filter(s -> s.category() == StatusCategory.DONE).findFirst()
                .orElseThrow().id();

        MvcResult updateResult = mockMvc.perform(patch("/api/v1/projects/{projectKey}/workflow", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateWorkflowSchemeRequest(statusEditsPreserving(scheme), List.of()))))
                .andExpect(status().isOk())
                .andReturn();
        WorkflowSchemeResponse updated = objectMapper.readValue(updateResult.getResponse().getContentAsString(),
                WorkflowSchemeResponse.class);
        assertThat(updated.transitions()).isEmpty();

        // The bug: getSchemeForProject's backfill used to treat "zero transitions" alone as "never
        // seeded" and would re-populate any-to-any right here.
        WorkflowSchemeResponse afterRead = getScheme(key, token);
        assertThat(afterRead.transitions()).isEmpty();

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        IssueResponse issue = objectMapper.readValue(created.getResponse().getContentAsString(), IssueResponse.class);
        assertThat(issue.statusId()).isEqualTo(todoStatusId);

        // Every transition is illegal now (deliberately locked down) — including one that the seeded
        // any-to-any graph would otherwise have allowed.
        mockMvc.perform(patch("/api/v1/issues/{issueKey}/status", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeIssueStatusRequest(doneStatusId))))
                .andExpect(status().isConflict());

        WorkflowSchemeResponse afterStatusChangeAttempt = getScheme(key, token);
        assertThat(afterStatusChangeAttempt.transitions()).isEmpty();
    }

    /**
     * Regression for the lazy any-to-any backfill: a scheme persisted directly (bypassing the
     * lazy-create path entirely, simulating one that predates Phase 4) has zero transitions until its
     * first read, at which point they must be seeded rather than leaving every status change
     * permanently illegal.
     */
    @Test
    void getBackfillsAnyToAnyTransitionsForASchemeWithNonePersistedYet() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        Project project = createProject(key, userOf(email));

        WorkflowScheme scheme = workflowSchemeRepository.save(new WorkflowScheme(project.getId(), "Default Workflow"));
        workflowStatusRepository.save(new WorkflowStatus(scheme, "To Do", StatusCategory.TODO, 0));
        workflowStatusRepository.save(new WorkflowStatus(scheme, "Done", StatusCategory.DONE, 1));

        WorkflowSchemeResponse result = getScheme(key, token);

        // The read also backfills the missing "Blocked" status (see the BLOCKED-backfill test elsewhere),
        // so any-to-any over the resulting 3 statuses = exactly 6 directed pairs.
        assertThat(result.statuses()).hasSize(3);
        assertThat(result.transitions()).hasSize(6);
    }
}
