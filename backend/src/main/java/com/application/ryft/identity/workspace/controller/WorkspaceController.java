package com.application.ryft.identity.workspace.controller;

import com.application.ryft.identity.workspace.dto.ChangeRoleRequest;
import com.application.ryft.identity.workspace.dto.CreateWorkspaceRequest;
import com.application.ryft.identity.workspace.dto.InviteRequest;
import com.application.ryft.identity.workspace.dto.WorkspaceMemberDTO;
import com.application.ryft.identity.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping("/setup")
    public ResponseEntity<WorkspaceMemberDTO> completeSetup(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateWorkspaceRequest request) {
        WorkspaceMemberDTO member = workspaceService.completeSetup(callerId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @GetMapping("/members")
    public ResponseEntity<List<WorkspaceMemberDTO>> members(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(workspaceService.listMembers(callerId(jwt)));
    }

    @PostMapping("/invite")
    public ResponseEntity<WorkspaceMemberDTO> invite(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InviteRequest request) {
        WorkspaceMemberDTO member = workspaceService.invite(callerId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @PatchMapping("/members/{userId}")
    public ResponseEntity<WorkspaceMemberDTO> changeRole(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId, @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(workspaceService.changeRole(callerId(jwt), userId, request));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
