package com.application.ryft.workflow.controller;

import com.application.ryft.workflow.dto.UpdateWorkflowSchemeRequest;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * Owner/Admin only. Accepts a full statuses+transitions diff — see
     * {@code WorkflowServiceImpl.updateScheme}'s javadoc for the exact create/update/delete rules and the
     * v1 restriction that a transition can't reference a status created in this same request.
     */
    @PatchMapping
    public ResponseEntity<WorkflowSchemeResponse> update(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String projectKey, @Valid @RequestBody UpdateWorkflowSchemeRequest request) {
        return ResponseEntity.ok(workflowService.updateScheme(callerId(jwt), projectKey, request));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
