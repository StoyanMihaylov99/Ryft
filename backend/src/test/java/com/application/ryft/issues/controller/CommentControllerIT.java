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
import com.application.ryft.issues.dto.CommentResponse;
import com.application.ryft.issues.dto.CreateCommentRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.UpdateCommentRequest;
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
class CommentControllerIT extends AbstractIntegrationTest {

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

    private String createIssue(String projectKey, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectKey}/issues", projectKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), IssueResponse.class).key();
    }

    @Test
    void createCommentSucceedsForProjectMember() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        String issueKey = createIssue(key, token);

        MvcResult result = mockMvc.perform(post("/api/v1/issues/{issueKey}/comments", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("First comment"))))
                .andExpect(status().isCreated())
                .andReturn();

        CommentResponse comment = objectMapper.readValue(result.getResponse().getContentAsString(), CommentResponse.class);
        assertThat(comment.body()).isEqualTo("First comment");
        assertThat(comment.authorId()).isEqualTo(userOf(email).getId());
    }

    @Test
    void createCommentByNonMemberReturns403() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        createProject(key, userOf(ownerEmail));
        String issueKey = createIssue(key, ownerToken);

        String outsiderToken = registerAndGetToken(uniqueEmail());

        mockMvc.perform(post("/api/v1/issues/{issueKey}/comments", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("Hi"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCommentOnUnknownIssueReturns404() throws Exception {
        String token = registerAndGetToken(uniqueEmail());

        mockMvc.perform(post("/api/v1/issues/{issueKey}/comments", "NOPE-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("Hi"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsCommentsInCreationOrder() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        String issueKey = createIssue(key, token);

        mockMvc.perform(post("/api/v1/issues/{issueKey}/comments", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("First"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/issues/{issueKey}/comments", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("Second"))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/issues/{issueKey}/comments", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        CommentResponse[] comments = objectMapper.readValue(result.getResponse().getContentAsString(), CommentResponse[].class);
        assertThat(comments).hasSize(2);
        assertThat(comments[0].body()).isEqualTo("First");
        assertThat(comments[1].body()).isEqualTo("Second");
    }

    @Test
    void authorCanEditTheirOwnComment() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        String issueKey = createIssue(key, token);

        MvcResult created = mockMvc.perform(post("/api/v1/issues/{issueKey}/comments", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("Original"))))
                .andExpect(status().isCreated())
                .andReturn();
        CommentResponse comment = objectMapper.readValue(created.getResponse().getContentAsString(), CommentResponse.class);

        MvcResult updated = mockMvc.perform(patch("/api/v1/comments/{commentId}", comment.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("Edited"))))
                .andExpect(status().isOk())
                .andReturn();
        CommentResponse result = objectMapper.readValue(updated.getResponse().getContentAsString(), CommentResponse.class);
        assertThat(result.body()).isEqualTo("Edited");
    }

    @Test
    void otherProjectMemberCannotEditSomeoneElsesComment() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        String key = uniqueKey();
        Project project = createProject(key, userOf(ownerEmail));
        String issueKey = createIssue(key, ownerToken);

        MvcResult created = mockMvc.perform(post("/api/v1/issues/{issueKey}/comments", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("Original"))))
                .andExpect(status().isCreated())
                .andReturn();
        CommentResponse comment = objectMapper.readValue(created.getResponse().getContentAsString(), CommentResponse.class);

        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        addMembership(project, userOf(memberEmail), ProjectRole.MEMBER);

        mockMvc.perform(patch("/api/v1/comments/{commentId}", comment.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("Hijacked"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorCanDeleteTheirOwnComment() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        String key = uniqueKey();
        createProject(key, userOf(email));
        String issueKey = createIssue(key, token);

        MvcResult created = mockMvc.perform(post("/api/v1/issues/{issueKey}/comments", issueKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("Original"))))
                .andExpect(status().isCreated())
                .andReturn();
        CommentResponse comment = objectMapper.readValue(created.getResponse().getContentAsString(), CommentResponse.class);

        mockMvc.perform(delete("/api/v1/comments/{commentId}", comment.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateOfUnknownCommentReturns404() throws Exception {
        String token = registerAndGetToken(uniqueEmail());

        mockMvc.perform(patch("/api/v1/comments/{commentId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("Edited"))))
                .andExpect(status().isNotFound());
    }
}
