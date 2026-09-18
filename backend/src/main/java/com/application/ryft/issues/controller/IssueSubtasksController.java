package com.application.ryft.issues.controller;

import com.application.ryft.issues.dto.CreateSubtaskRequest;
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

/**
 * The subtask collection nested under an issue — mirrors {@link IssueCommentsController}'s split from
 * the flat issue-key resource. A subtask is addressed by its own key everywhere else (get/update/status/
 * delete all go through {@link IssueController}); this controller only covers create/list under a parent.
 */
@RestController
@RequestMapping("/api/v1/issues/{issueKey}/subtasks")
public class IssueSubtasksController {

    private final IssueService issueService;

    public IssueSubtasksController(IssueService issueService) {
        this.issueService = issueService;
    }

    @PostMapping
    public ResponseEntity<IssueResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueKey,
            @Valid @RequestBody CreateSubtaskRequest request) {
        IssueResponse subtask = issueService.createSubtask(callerId(jwt), issueKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(subtask);
    }

    @GetMapping
    public ResponseEntity<List<IssueResponse>> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueKey) {
        return ResponseEntity.ok(issueService.listSubtasks(callerId(jwt), issueKey));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
