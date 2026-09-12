package com.application.ryft.projects.controller;

import com.application.ryft.projects.dto.AddProjectMemberRequest;
import com.application.ryft.projects.dto.ChangeProjectMemberRoleRequest;
import com.application.ryft.projects.dto.CreateProjectRequest;
import com.application.ryft.projects.dto.ProjectDTO;
import com.application.ryft.projects.dto.ProjectMemberDTO;
import com.application.ryft.projects.dto.UpdateProjectRequest;
import com.application.ryft.projects.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectDTO> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectDTO project = projectService.create(callerId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @GetMapping
    public ResponseEntity<List<ProjectDTO>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(projectService.listForCaller(callerId(jwt)));
    }

    @GetMapping("/{projectKey}")
    public ResponseEntity<ProjectDTO> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey) {
        return ResponseEntity.ok(projectService.get(callerId(jwt), projectKey));
    }

    @PatchMapping("/{projectKey}")
    public ResponseEntity<ProjectDTO> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.update(callerId(jwt), projectKey, request));
    }

    @DeleteMapping("/{projectKey}")
    public ResponseEntity<Void> archive(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey) {
        projectService.archive(callerId(jwt), projectKey);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectKey}/members")
    public ResponseEntity<List<ProjectMemberDTO>> members(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String projectKey) {
        return ResponseEntity.ok(projectService.listMembers(callerId(jwt), projectKey));
    }

    @PostMapping("/{projectKey}/members")
    public ResponseEntity<ProjectMemberDTO> addMember(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String projectKey, @Valid @RequestBody AddProjectMemberRequest request) {
        ProjectMemberDTO member = projectService.addMember(callerId(jwt), projectKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @PatchMapping("/{projectKey}/members/{userId}")
    public ResponseEntity<ProjectMemberDTO> changeMemberRole(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String projectKey, @PathVariable UUID userId,
            @Valid @RequestBody ChangeProjectMemberRoleRequest request) {
        return ResponseEntity.ok(projectService.changeMemberRole(callerId(jwt), projectKey, userId, request));
    }

    @DeleteMapping("/{projectKey}/members/{userId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey,
            @PathVariable UUID userId) {
        projectService.removeMember(callerId(jwt), projectKey, userId);
        return ResponseEntity.noContent().build();
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
