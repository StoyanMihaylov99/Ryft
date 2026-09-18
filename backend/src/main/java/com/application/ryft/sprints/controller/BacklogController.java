package com.application.ryft.sprints.controller;

import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.sprints.service.BacklogService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The project's backlog (issues with no sprint) — see {@link IssueSprintController} for moving an issue. */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/backlog")
public class BacklogController {

    private final BacklogService backlogService;

    public BacklogController(BacklogService backlogService) {
        this.backlogService = backlogService;
    }

    @GetMapping
    public ResponseEntity<List<IssueResponse>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey) {
        return ResponseEntity.ok(backlogService.listBacklog(callerId(jwt), projectKey));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
