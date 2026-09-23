package com.application.ryft.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.application.ryft.AbstractIntegrationTest;
import com.application.ryft.identity.auth.dto.RegisterRequest;
import com.application.ryft.identity.user.entity.User;
import com.application.ryft.identity.user.repository.UserRepository;
import com.application.ryft.identity.workspace.entity.Workspace;
import com.application.ryft.identity.workspace.repository.WorkspaceRepository;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.projects.entity.Project;
import com.application.ryft.projects.entity.ProjectMember;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
import com.application.ryft.search.dto.CreateSavedFilterRequest;
import com.application.ryft.search.dto.IssueSearchRequest;
import com.application.ryft.search.dto.SavedFilterResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Covers {@code GET}/{@code POST /projects/{projectKey}/filters} and {@code DELETE .../filters/{filterId}}. */
@AutoConfigureMockMvc
class SavedFilterControllerIT extends AbstractIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

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

    private SavedFilterResponse create(String projectKey, String token, CreateSavedFilterRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/filters", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), SavedFilterResponse.class);
    }

    private SavedFilterResponse[] list(String projectKey, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectKey}/filters", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), SavedFilterResponse[].class);
    }

    @Test
    void createPersistsAndRoundTripsTheStructuredQueryThroughTheJsonColumn() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        UUID assigneeId = UUID.randomUUID();
        UUID statusId = UUID.randomUUID();
        UUID labelId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        IssueSearchRequest query = new IssueSearchRequest(List.of(assigneeId), List.of(statusId), List.of(labelId),
                List.of(componentId), List.of(IssueType.BUG, IssueType.STORY), List.of(sprintId), null);

        SavedFilterResponse created = create(key, ownerToken, new CreateSavedFilterRequest("My Bugs", query, false));
        assertThat(created.query()).isEqualTo(query);

        SavedFilterResponse[] fetched = list(key, ownerToken);

        assertThat(fetched).hasSize(1);
        assertThat(fetched[0].id()).isEqualTo(created.id());
        assertThat(fetched[0].name()).isEqualTo("My Bugs");
        assertThat(fetched[0].isShared()).isFalse();
        assertThat(fetched[0].query()).isEqualTo(query);
    }

    @Test
    void listReturnsCallersOwnFiltersAndOthersSharedFiltersButNotOthersPrivateOnes() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        IssueSearchRequest emptyQuery = new IssueSearchRequest(null, null, null, null, null, null, null);
        create(key, ownerToken, new CreateSavedFilterRequest("Owner Private", emptyQuery, false));
        create(key, ownerToken, new CreateSavedFilterRequest("Owner Shared", emptyQuery, true));
        create(key, memberToken, new CreateSavedFilterRequest("Member Private", emptyQuery, false));

        SavedFilterResponse[] ownerView = list(key, ownerToken);
        assertThat(ownerView).extracting(SavedFilterResponse::name)
                .containsExactlyInAnyOrder("Owner Private", "Owner Shared");

        SavedFilterResponse[] memberView = list(key, memberToken);
        assertThat(memberView).extracting(SavedFilterResponse::name)
                .containsExactlyInAnyOrder("Owner Shared", "Member Private");
    }

    /**
     * {@link #listReturnsCallersOwnFiltersAndOthersSharedFiltersButNotOthersPrivateOnes} only exercises
     * one project, so it can't distinguish the correct {@code projectId AND (ownerId OR isShared)} query
     * from the leaky {@code (projectId AND ownerId) OR isShared} shape — a project-shared filter would
     * still (wrongly) show up in another project's list under the leaky shape, since {@code isShared}
     * alone would match regardless of project. This test pins two distinct projects, each with its own
     * shared filter from a different owner, and asserts neither project's list ever includes the other's.
     */
    @Test
    void listNeverLeaksASharedFilterFromAnotherProject() throws Exception {
        String ownerAEmail = uniqueEmail();
        String ownerAToken = registerAndGetToken(ownerAEmail);
        String keyA = uniqueKey();
        Project projectA = createProject(keyA, userOf(ownerAEmail));

        String ownerBEmail = uniqueEmail();
        String ownerBToken = registerAndGetToken(ownerBEmail);
        String keyB = uniqueKey();
        Project projectB = createProject(keyB, userOf(ownerBEmail));

        String memberAEmail = uniqueEmail();
        String memberAToken = registerAndGetToken(memberAEmail);
        addMembership(projectA, userOf(memberAEmail), ProjectRole.MEMBER);

        IssueSearchRequest emptyQuery = new IssueSearchRequest(null, null, null, null, null, null, null);
        create(keyA, ownerAToken, new CreateSavedFilterRequest("A Shared", emptyQuery, true));
        create(keyB, ownerBToken, new CreateSavedFilterRequest("B Shared", emptyQuery, true));

        SavedFilterResponse[] memberAView = list(keyA, memberAToken);
        assertThat(memberAView).extracting(SavedFilterResponse::name).containsExactly("A Shared");

        SavedFilterResponse[] ownerAView = list(keyA, ownerAToken);
        assertThat(ownerAView).extracting(SavedFilterResponse::name).containsExactly("A Shared");

        SavedFilterResponse[] ownerBView = list(keyB, ownerBToken);
        assertThat(ownerBView).extracting(SavedFilterResponse::name).containsExactly("B Shared");
    }

    @Test
    void createPrivateFilterSucceedsForViewer() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String viewerEmail = uniqueEmail();
        String viewerToken = registerAndGetToken(viewerEmail);
        addMembership(project, userOf(viewerEmail), ProjectRole.VIEWER);

        IssueSearchRequest emptyQuery = new IssueSearchRequest(null, null, null, null, null, null, null);
        mockMvc.perform(post("/api/v1/projects/{projectKey}/filters", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSavedFilterRequest("Viewer's Own", emptyQuery, false))))
                .andExpect(status().isCreated());
    }

    @Test
    void createSharedFilterByViewerReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String viewerEmail = uniqueEmail();
        String viewerToken = registerAndGetToken(viewerEmail);
        addMembership(project, userOf(viewerEmail), ProjectRole.VIEWER);

        IssueSearchRequest emptyQuery = new IssueSearchRequest(null, null, null, null, null, null, null);
        mockMvc.perform(post("/api/v1/projects/{projectKey}/filters", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSavedFilterRequest("Team Filter", emptyQuery, true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        IssueSearchRequest emptyQuery = new IssueSearchRequest(null, null, null, null, null, null, null);
        mockMvc.perform(post("/api/v1/projects/{projectKey}/filters", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSavedFilterRequest("  ", emptyQuery, false))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteByOwnerSucceeds() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        IssueSearchRequest emptyQuery = new IssueSearchRequest(null, null, null, null, null, null, null);
        SavedFilterResponse created = create(key, ownerToken, new CreateSavedFilterRequest("Mine", emptyQuery, false));

        mockMvc.perform(delete("/api/v1/projects/{projectKey}/filters/{filterId}", key, created.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(list(key, ownerToken)).isEmpty();
    }

    @Test
    void deleteByNonOwnerReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        IssueSearchRequest emptyQuery = new IssueSearchRequest(null, null, null, null, null, null, null);
        SavedFilterResponse created = create(key, ownerToken, new CreateSavedFilterRequest("Owner's", emptyQuery, true));

        mockMvc.perform(delete("/api/v1/projects/{projectKey}/filters/{filterId}", key, created.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    /**
     * {@code delete} is owner-only with no role override — unlike issues/labels/components, where
     * Owner/Admin manage the resource unrestricted (see {@code IssueProjectAccess.isOwnerOrAdmin}'s
     * javadoc), a saved filter follows the comment "author only" convention instead. This pins that
     * specifically against the Owner role (the one role that *does* get elevated rights elsewhere in
     * this codebase), not just a plain Member, so a future accidental admin-override regression here
     * would fail this test even though {@link #deleteByNonOwnerReturns403} alone wouldn't catch it.
     */
    @Test
    void deleteByProjectOwnerOfAnotherMembersFilterReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        IssueSearchRequest emptyQuery = new IssueSearchRequest(null, null, null, null, null, null, null);
        SavedFilterResponse created = create(key, memberToken, new CreateSavedFilterRequest("Member's", emptyQuery, true));

        mockMvc.perform(delete("/api/v1/projects/{projectKey}/filters/{filterId}", key, created.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isForbidden());

        assertThat(list(key, memberToken)).extracting(SavedFilterResponse::name).containsExactly("Member's");
    }

    @Test
    void deleteOfNonexistentFilterReturns404() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        mockMvc.perform(delete("/api/v1/projects/{projectKey}/filters/{filterId}", key, UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteOfFilterFromAnotherProjectReturns404() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String keyA = uniqueKey();
        String keyB = uniqueKey();
        createProject(keyA, userOf(ownerEmail));
        createProject(keyB, userOf(ownerEmail));

        IssueSearchRequest emptyQuery = new IssueSearchRequest(null, null, null, null, null, null, null);
        SavedFilterResponse created = create(keyA, ownerToken, new CreateSavedFilterRequest("In A", emptyQuery, false));

        mockMvc.perform(delete("/api/v1/projects/{projectKey}/filters/{filterId}", keyB, created.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listByNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));

        String outsiderToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/v1/projects/{projectKey}/filters", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listForUnknownProjectReturns404() throws Exception {
        String token = registerAndGetToken(uniqueEmail());

        mockMvc.perform(get("/api/v1/projects/{projectKey}/filters", uniqueKey())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    /**
     * {@code query}'s {@code jsonb} column shape grew a {@code text} field in this change — a filter saved
     * before that (whose stored JSON has no {@code "text"} key at all, not even {@code "text": null}) must
     * still deserialize cleanly through {@code SavedFilter.query}'s {@code Jackson3JsonFormatMapper}
     * mapping, with the missing field simply defaulting to {@code null} on the record. Row inserted
     * directly via JDBC, bypassing the entity/DTO layer entirely, so the JSON really has no {@code text}
     * key rather than one this test's own (post-change) code would always include.
     */
    @Test
    void anExistingSavedFilterJsonWithoutATextKeyStillDeserializesWithTextDefaultingToNull() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));
        User owner = userOf(ownerEmail);

        String legacyQueryJson = "{\"assigneeIds\":null,\"statusIds\":null,\"labelIds\":null,"
                + "\"componentIds\":null,\"types\":null,\"sprintIds\":null}";
        jdbcTemplate.update("""
                insert into saved_filters (id, project_id, owner_id, name, query, is_shared, created_at)
                values (?, ?, ?, ?, ?::jsonb, ?, now())
                """, UUID.randomUUID(), project.getId(), owner.getId(), "Legacy Filter", legacyQueryJson, false);

        SavedFilterResponse[] fetched = list(key, ownerToken);

        assertThat(fetched).hasSize(1);
        assertThat(fetched[0].name()).isEqualTo("Legacy Filter");
        assertThat(fetched[0].query().text()).isNull();
        assertThat(fetched[0].query().assigneeIds()).isNull();
    }
}
