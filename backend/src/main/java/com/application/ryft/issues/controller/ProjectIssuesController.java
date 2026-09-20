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
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * {@code sprintId}/{@code epicId}/{@code labelId}/{@code componentId} are mutually exclusive filters
     * applied with that precedence, matching the existing {@code sprintId}-wins-over-{@code epicId} rule
     * from Phase 3's first hierarchy step: passing more than one only applies the highest-precedence one
     * present. Combining them with AND semantics would need a dynamic query (Criteria/Specification) —
     * a pattern this codebase doesn't use anywhere else — so a simple precedence chain was kept instead,
     * consistent with how {@code epicId} was already layered onto {@code sprintId}.
     */
    @GetMapping
    public ResponseEntity<List<IssueResponse>> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey,
            @RequestParam(required = false) UUID sprintId, @RequestParam(required = false) UUID epicId,
            @RequestParam(required = false) UUID labelId, @RequestParam(required = false) UUID componentId) {
        UUID caller = callerId(jwt);
        List<IssueResponse> issues;
        if (sprintId != null) {
            issues = issueService.listForProject(caller, projectKey, sprintId);
        } else if (epicId != null) {
            issues = issueService.listForProjectByEpic(caller, projectKey, epicId);
        } else if (labelId != null) {
            issues = issueService.listForProjectByLabel(caller, projectKey, labelId);
        } else if (componentId != null) {
            issues = issueService.listForProjectByComponent(caller, projectKey, componentId);
        } else {
            issues = issueService.listForProject(caller, projectKey);
        }
        return ResponseEntity.ok(issues);
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
