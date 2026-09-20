package com.application.ryft.issues.controller;

import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.service.IssueService;
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

/** The issue collection nested under a project — see {@link IssueController} for the flat issue-key resource. */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/issues")
public class ProjectIssuesController {

    private final IssueService issueService;

    public ProjectIssuesController(IssueService issueService) {
        this.issueService = issueService;
    }

    @PostMapping
    public ResponseEntity<IssueResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey,
            @Valid @RequestBody CreateIssueRequest request) {
        IssueResponse issue = issueService.create(callerId(jwt), projectKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(issue);
    }

    @GetMapping
    public ResponseEntity<List<IssueResponse>> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey) {
        return ResponseEntity.ok(issueService.listForProject(callerId(jwt), projectKey));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
