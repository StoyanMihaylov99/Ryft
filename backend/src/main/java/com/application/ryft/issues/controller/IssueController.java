package com.application.ryft.issues.controller;

import com.application.ryft.issues.dto.ChangeIssueStatusRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.ReorderBacklogIssueRequest;
import com.application.ryft.issues.dto.UpdateIssueRequest;
import com.application.ryft.issues.service.IssueService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The flat issue-key resource (e.g. "TRK-142") — see {@link ProjectIssuesController} for create/list. */
@RestController
@RequestMapping("/api/v1/issues")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @GetMapping("/{issueKey}")
    public ResponseEntity<IssueResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueKey) {
        return ResponseEntity.ok(issueService.get(callerId(jwt), issueKey));
    }

    @PatchMapping("/{issueKey}")
    public ResponseEntity<IssueResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueKey,
            @Valid @RequestBody UpdateIssueRequest request) {
        return ResponseEntity.ok(issueService.update(callerId(jwt), issueKey, request));
    }

    @PatchMapping("/{issueKey}/status")
    public ResponseEntity<IssueResponse> updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueKey,
            @Valid @RequestBody ChangeIssueStatusRequest request) {
        return ResponseEntity.ok(issueService.changeStatus(callerId(jwt), issueKey, request));
    }

    @PatchMapping("/{issueKey}/backlog-rank")
    public ResponseEntity<IssueResponse> reorderBacklogRank(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String issueKey, @Valid @RequestBody ReorderBacklogIssueRequest request) {
        return ResponseEntity.ok(issueService.reorderBacklog(callerId(jwt), issueKey, request.beforeIssueKey(),
                request.afterIssueKey()));
    }

    @DeleteMapping("/{issueKey}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueKey) {
        issueService.delete(callerId(jwt), issueKey);
        return ResponseEntity.noContent().build();
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
