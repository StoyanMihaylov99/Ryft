package com.application.ryft.issues.controller;

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
import com.application.ryft.issues.dto.ComponentResponse;
import com.application.ryft.issues.dto.CreateComponentRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.CreateLabelRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.LabelResponse;
import com.application.ryft.issues.dto.UpdateIssueRequest;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.projects.entity.Project;
import com.application.ryft.projects.entity.ProjectMember;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
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
 * Covers attaching labels/components to an issue on create/update and filtering the project issue
 * list by them — see {@link ProjectLabelsControllerIT}/{@link ProjectComponentsControllerIT} for the
 * label/component CRUD endpoints themselves.
 */
@AutoConfigureMockMvc
class IssueLabelingControllerIT extends AbstractIntegrationTest {

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

    private IssueResponse createIssue(String projectKey, String token, List<UUID> labelIds, List<UUID> componentIds)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateIssueRequest(IssueType.TASK, "Title", null,
                                null, null, null, null, labelIds, componentIds))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse.class);
    }

    @Test
    void creatingAnIssueWithLabelsAndComponentsRoundTripsInTheResponse() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        LabelResponse label = createLabel(key, token, "Bug");
        ComponentResponse component = createComponent(key, token, "Backend");

        IssueResponse issue = createIssue(key, token, List.of(label.id()), List.of(component.id()));

        assertThat(issue.labels()).extracting(LabelResponse::id).containsExactly(label.id());
        assertThat(issue.components()).extracting(ComponentResponse::id).containsExactly(component.id());
    }

    @Test
    void creatingAnIssueWithLabelFromAnotherProjectReturns400() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        String otherKey = uniqueKey();
        createProject(otherKey, userOf(email));
        LabelResponse foreignLabel = createLabel(otherKey, token, "Bug");

        mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", key)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateIssueRequest(IssueType.TASK, "Title", null,
                                null, null, null, null, List.of(foreignLabel.id()), null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatingAnIssueWithOmittedLabelIdsLeavesExistingLabelsUnchanged() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        LabelResponse label = createLabel(key, token, "Bug");
        IssueResponse issue = createIssue(key, token, List.of(label.id()), null);

        MvcResult updated = mockMvc.perform(patch("/api/v1/issues/{issueKey}", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateIssueRequest("New title", null, null, null, null, null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse result = objectMapper.readValue(updated.getResponse().getContentAsString(), IssueResponse.class);

        assertThat(result.labels()).extracting(LabelResponse::id).containsExactly(label.id());
    }

    @Test
    void updatingAnIssueWithEmptyLabelIdsClearsExistingLabels() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        LabelResponse label = createLabel(key, token, "Bug");
        IssueResponse issue = createIssue(key, token, List.of(label.id()), null);

        MvcResult updated = mockMvc.perform(patch("/api/v1/issues/{issueKey}", issue.key())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateIssueRequest(null, null, null, null, null, null, List.of(), null))))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse result = objectMapper.readValue(updated.getResponse().getContentAsString(), IssueResponse.class);

        assertThat(result.labels()).isEmpty();
    }

    @Test
    void listIssuesFilteredByLabelIdReturnsOnlyMatchingIssues() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        LabelResponse label = createLabel(key, token, "Bug");
        IssueResponse labeled = createIssue(key, token, List.of(label.id()), null);
        createIssue(key, token, null, null);

        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectKey}/issues", key)
                        .param("labelId", label.id().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse[] issues = objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse[].class);

        assertThat(issues).extracting(IssueResponse::key).containsExactly(labeled.key());
    }

    @Test
    void listIssuesFilteredByComponentIdReturnsOnlyMatchingIssues() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        ComponentResponse component = createComponent(key, token, "Backend");
        IssueResponse tagged = createIssue(key, token, null, List.of(component.id()));
        createIssue(key, token, null, null);

        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectKey}/issues", key)
                        .param("componentId", component.id().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        IssueResponse[] issues = objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse[].class);

        assertThat(issues).extracting(IssueResponse::key).containsExactly(tagged.key());
    }
}
