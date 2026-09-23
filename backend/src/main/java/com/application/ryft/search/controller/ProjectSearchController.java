package com.application.ryft.search.controller;

import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.search.dto.IssueSearchRequest;
import com.application.ryft.search.service.ProjectSearchService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Structured issue search — the AND-combining counterpart to {@code issues.controller.ProjectIssuesController}'s
 * GET precedence-chain filters (see that controller's javadoc). A {@code POST} with a body rather than a
 * {@code GET} with query params: {@code IssueSearchRequest} carries up to six list-valued filters, which
 * doesn't fit cleanly into query string repetition/comma-separation the way a single-valued filter does.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/search")
public class ProjectSearchController {

    private final ProjectSearchService searchService;

    public ProjectSearchController(ProjectSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping
    public ResponseEntity<List<IssueResponse>> search(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String projectKey, @Valid @RequestBody IssueSearchRequest request) {
        List<IssueResponse> issues = searchService.search(callerId(jwt), projectKey, request);
        return ResponseEntity.ok(issues);
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
