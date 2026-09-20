package com.application.ryft.sprints.controller;

import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.sprints.dto.MoveIssueToSprintRequest;
import com.application.ryft.sprints.service.BacklogService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Moves a single issue between the backlog and a sprint — see {@link BacklogController} for the backlog list. */
@RestController
@RequestMapping("/api/v1/issues")
public class IssueSprintController {

    private final BacklogService backlogService;

    public IssueSprintController(BacklogService backlogService) {
        this.backlogService = backlogService;
    }

    @PatchMapping("/{issueKey}/sprint")
    public ResponseEntity<IssueResponse> moveSprint(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueKey,
            @Valid @RequestBody MoveIssueToSprintRequest request) {
        return ResponseEntity.ok(backlogService.moveIssue(callerId(jwt), issueKey, request.sprintId()));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
