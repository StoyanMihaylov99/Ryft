package com.application.ryft.sprints.controller;

import com.application.ryft.sprints.dto.SprintResponse;
import com.application.ryft.sprints.dto.UpdateSprintRequest;
import com.application.ryft.sprints.service.SprintService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The flat sprint-id resource — see {@link ProjectSprintsController} for create/list. */
@RestController
@RequestMapping("/api/v1/sprints")
public class SprintController {

    private final SprintService sprintService;

    public SprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @PatchMapping("/{sprintId}")
    public ResponseEntity<SprintResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId,
            @Valid @RequestBody UpdateSprintRequest request) {
        return ResponseEntity.ok(sprintService.update(callerId(jwt), sprintId, request));
    }

    @PostMapping("/{sprintId}/start")
    public ResponseEntity<SprintResponse> start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId) {
        return ResponseEntity.ok(sprintService.start(callerId(jwt), sprintId));
    }

    @PostMapping("/{sprintId}/complete")
    public ResponseEntity<SprintResponse> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId) {
        return ResponseEntity.ok(sprintService.complete(callerId(jwt), sprintId));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
