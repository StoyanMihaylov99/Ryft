package com.application.ryft.workflow.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.application.ryft.AbstractIntegrationTest;
import com.application.ryft.identity.auth.dto.RegisterRequest;
import com.application.ryft.identity.user.entity.User;
import com.application.ryft.identity.user.repository.UserRepository;
import com.application.ryft.identity.workspace.entity.Workspace;
import com.application.ryft.identity.workspace.repository.WorkspaceRepository;
import com.application.ryft.projects.entity.Project;
import com.application.ryft.projects.entity.ProjectMember;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.entity.StatusCategory;
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
        assertThat(scheme.statuses()).hasSize(3);
        assertThat(scheme.statuses().get(0).name()).isEqualTo("To Do");
        assertThat(scheme.statuses().get(0).category()).isEqualTo(StatusCategory.TODO);
        assertThat(scheme.statuses().get(1).name()).isEqualTo("In Progress");
        assertThat(scheme.statuses().get(1).category()).isEqualTo(StatusCategory.IN_PROGRESS);
        assertThat(scheme.statuses().get(2).name()).isEqualTo("Done");
        assertThat(scheme.statuses().get(2).category()).isEqualTo(StatusCategory.DONE);
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
        assertThat(secondScheme.statuses()).hasSize(3);
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
}
