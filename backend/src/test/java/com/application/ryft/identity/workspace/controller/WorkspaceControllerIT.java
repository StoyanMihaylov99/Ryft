package com.application.ryft.identity.workspace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.application.ryft.AbstractIntegrationTest;
import com.application.ryft.identity.workspace.dto.ChangeRoleRequest;
import com.application.ryft.identity.workspace.dto.CreateWorkspaceRequest;
import com.application.ryft.identity.workspace.dto.InviteRequest;
import com.application.ryft.identity.auth.dto.RegisterRequest;
import com.application.ryft.identity.workspace.dto.WorkspaceMemberDTO;
import com.application.ryft.identity.user.repository.UserRepository;
import com.application.ryft.identity.workspace.repository.WorkspaceMemberRepository;
import com.application.ryft.identity.workspace.repository.WorkspaceRepository;
import com.application.ryft.identity.user.entity.User;
import com.application.ryft.identity.workspace.entity.Workspace;
import com.application.ryft.identity.workspace.entity.WorkspaceMember;
import com.application.ryft.identity.workspace.entity.WorkspaceRole;
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
 * Because {@link AbstractIntegrationTest} shares one Postgres container across every test in the
 * JVM, a workspace created by an earlier test method may still be sitting there when a later one
 * runs. Most tests here set up the exact membership rows they need directly via the repositories
 * (see {@link #theWorkspace()}/{@link #setMembership}) rather than relying on any particular
 * ordering. The two tests that need to observe *no* workspace existing yet call
 * {@link #clearWorkspace()} first — safe because no other IT class touches these tables.
 */
@AutoConfigureMockMvc
class WorkspaceControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
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

    /** Gets the workspace, or creates a default one directly (test scaffolding, not exercising /setup). */
    private Workspace theWorkspace() {
        return workspaceRepository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(() -> workspaceRepository.save(new Workspace("Ryft", "ryft")));
    }

    /** Deletes the workspace (and its members) so a test can exercise genuine first-time setup. */
    private void clearWorkspace() {
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
    }

    /** Forces {@code user}'s membership to exactly {@code role}, regardless of what bootstrap did. */
    private void setMembership(User user, WorkspaceRole role) {
        Workspace workspace = theWorkspace();
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId())
                .orElseGet(() -> new WorkspaceMember(workspace, user, role));
        member.setRole(role);
        workspaceMemberRepository.save(member);
    }

    private void removeMembership(User user) {
        Workspace workspace = theWorkspace();
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId())
                .ifPresent(workspaceMemberRepository::delete);
    }

    @Test
    void completeSetupCreatesWorkspaceAndMakesCallerOwner() throws Exception {
        clearWorkspace();
        String email = uniqueEmail();
        String token = registerAndGetToken(email);

        MvcResult result = mockMvc.perform(post("/api/v1/workspace/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWorkspaceRequest("Acme Inc", "acme"))))
                .andExpect(status().isCreated())
                .andReturn();

        WorkspaceMemberDTO member = objectMapper.readValue(
                result.getResponse().getContentAsString(), WorkspaceMemberDTO.class);
        assertThat(member.role()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(member.email()).isEqualTo(email);
        assertThat(theWorkspace().getName()).isEqualTo("Acme Inc");
        assertThat(theWorkspace().getSlug()).isEqualTo("acme");
    }

    @Test
    void completeSetupFailsWhenAWorkspaceAlreadyExists() throws Exception {
        theWorkspace();
        String email = uniqueEmail();
        String token = registerAndGetToken(email);

        mockMvc.perform(post("/api/v1/workspace/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWorkspaceRequest("Acme Inc", "acme"))))
                .andExpect(status().isConflict());
    }

    @Test
    void listMembersReturns404WhenWorkspaceNotSetUpYet() throws Exception {
        clearWorkspace();
        String email = uniqueEmail();
        String token = registerAndGetToken(email);

        mockMvc.perform(get("/api/v1/workspace/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanListMembersIncludingSelf() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        setMembership(userOf(ownerEmail), WorkspaceRole.OWNER);

        MvcResult result = mockMvc.perform(get("/api/v1/workspace/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        List<WorkspaceMemberDTO> members = List.of(
                objectMapper.readValue(result.getResponse().getContentAsString(), WorkspaceMemberDTO[].class));
        assertThat(members).anySatisfy(member -> {
            assertThat(member.email()).isEqualTo(ownerEmail);
            assertThat(member.role()).isEqualTo(WorkspaceRole.OWNER);
        });
    }

    @Test
    void nonMemberCannotListMembers() throws Exception {
        String email = uniqueEmail();
        String token = registerAndGetToken(email);
        removeMembership(userOf(email));

        mockMvc.perform(get("/api/v1/workspace/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerInvitesExistingUserSuccessfully() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        setMembership(userOf(ownerEmail), WorkspaceRole.OWNER);

        String targetEmail = uniqueEmail();
        registerAndGetToken(targetEmail);
        removeMembership(userOf(targetEmail));

        mockMvc.perform(post("/api/v1/workspace/invite")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InviteRequest(targetEmail, WorkspaceRole.MEMBER))))
                .andExpect(status().isCreated());

        assertThat(workspaceMemberRepository.findByWorkspaceIdAndUserId(theWorkspace().getId(), userOf(targetEmail).getId()))
                .isPresent()
                .get()
                .satisfies(member -> assertThat(member.getRole()).isEqualTo(WorkspaceRole.MEMBER));
    }

    @Test
    void inviteByPlainMemberReturns403() throws Exception {
        String memberEmail = uniqueEmail();
        String memberToken = registerAndGetToken(memberEmail);
        setMembership(userOf(memberEmail), WorkspaceRole.MEMBER);

        String targetEmail = uniqueEmail();
        registerAndGetToken(targetEmail);
        removeMembership(userOf(targetEmail));

        mockMvc.perform(post("/api/v1/workspace/invite")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InviteRequest(targetEmail, WorkspaceRole.MEMBER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void inviteOfUnregisteredEmailReturns404() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        setMembership(userOf(ownerEmail), WorkspaceRole.OWNER);

        mockMvc.perform(post("/api/v1/workspace/invite")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InviteRequest(uniqueEmail(), WorkspaceRole.MEMBER))))
                .andExpect(status().isNotFound());
    }

    @Test
    void inviteOfExistingMemberReturns409() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        setMembership(userOf(ownerEmail), WorkspaceRole.OWNER);

        String targetEmail = uniqueEmail();
        registerAndGetToken(targetEmail);
        setMembership(userOf(targetEmail), WorkspaceRole.MEMBER);

        mockMvc.perform(post("/api/v1/workspace/invite")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InviteRequest(targetEmail, WorkspaceRole.MEMBER))))
                .andExpect(status().isConflict());
    }

    @Test
    void inviteTargetingOwnerRoleReturns400() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        setMembership(userOf(ownerEmail), WorkspaceRole.OWNER);

        String targetEmail = uniqueEmail();
        registerAndGetToken(targetEmail);
        removeMembership(userOf(targetEmail));

        mockMvc.perform(post("/api/v1/workspace/invite")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InviteRequest(targetEmail, WorkspaceRole.OWNER))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerChangesMemberRoleSuccessfully() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        setMembership(userOf(ownerEmail), WorkspaceRole.OWNER);

        String targetEmail = uniqueEmail();
        registerAndGetToken(targetEmail);
        User target = userOf(targetEmail);
        setMembership(target, WorkspaceRole.MEMBER);

        mockMvc.perform(patch("/api/v1/workspace/members/{userId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeRoleRequest(WorkspaceRole.ADMIN))))
                .andExpect(status().isOk());

        assertThat(workspaceMemberRepository.findByWorkspaceIdAndUserId(theWorkspace().getId(), target.getId()))
                .isPresent()
                .get()
                .satisfies(member -> assertThat(member.getRole()).isEqualTo(WorkspaceRole.ADMIN));
    }

    @Test
    void changeRoleByNonOwnerReturns403() throws Exception {
        String adminEmail = uniqueEmail();
        String adminToken = registerAndGetToken(adminEmail);
        setMembership(userOf(adminEmail), WorkspaceRole.ADMIN);

        String targetEmail = uniqueEmail();
        registerAndGetToken(targetEmail);
        User target = userOf(targetEmail);
        setMembership(target, WorkspaceRole.MEMBER);

        mockMvc.perform(patch("/api/v1/workspace/members/{userId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeRoleRequest(WorkspaceRole.MEMBER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCannotChangeOwnRole() throws Exception {
        String ownerEmail = uniqueEmail();
        String ownerToken = registerAndGetToken(ownerEmail);
        User owner = userOf(ownerEmail);
        setMembership(owner, WorkspaceRole.OWNER);

        mockMvc.perform(patch("/api/v1/workspace/members/{userId}", owner.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeRoleRequest(WorkspaceRole.MEMBER))))
                .andExpect(status().isBadRequest());
    }
}
