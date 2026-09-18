package com.application.ryft.sprints.controller;

import com.application.ryft.sprints.dto.CreateSprintRequest;
import com.application.ryft.sprints.dto.SprintResponse;
import com.application.ryft.sprints.service.SprintService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The sprint collection nested under a project — see {@link SprintController} for the flat sprint-id resource. */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/sprints")
public class ProjectSprintsController {

    private final SprintService sprintService;

    public ProjectSprintsController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @PostMapping
    public ResponseEntity<SprintResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey,
            @Valid @RequestBody CreateSprintRequest request) {
        SprintResponse sprint = sprintService.create(callerId(jwt), projectKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(sprint);
    }

    @GetMapping
    public ResponseEntity<List<SprintResponse>> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey) {
        return ResponseEntity.ok(sprintService.listForProject(callerId(jwt), projectKey));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
