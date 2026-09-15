package com.application.ryft.workflow.controller;

import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.service.WorkflowService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/workflow")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping
    public ResponseEntity<WorkflowSchemeResponse> get(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String projectKey) {
        return ResponseEntity.ok(workflowService.getSchemeForProject(callerId(jwt), projectKey));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
