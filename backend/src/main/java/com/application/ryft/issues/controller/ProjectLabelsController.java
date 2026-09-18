package com.application.ryft.issues.controller;

import com.application.ryft.issues.dto.CreateLabelRequest;
import com.application.ryft.issues.dto.LabelResponse;
import com.application.ryft.issues.dto.UpdateLabelRequest;
import com.application.ryft.issues.service.LabelService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/labels")
public class ProjectLabelsController {

    private final LabelService labelService;

    public ProjectLabelsController(LabelService labelService) {
        this.labelService = labelService;
    }

    @PostMapping
    public ResponseEntity<LabelResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey,
            @Valid @RequestBody CreateLabelRequest request) {
        LabelResponse label = labelService.create(callerId(jwt), projectKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(label);
    }

    @GetMapping
    public ResponseEntity<List<LabelResponse>> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey) {
        return ResponseEntity.ok(labelService.listForProject(callerId(jwt), projectKey));
    }

    @PatchMapping("/{labelId}")
    public ResponseEntity<LabelResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey,
            @PathVariable UUID labelId, @Valid @RequestBody UpdateLabelRequest request) {
        return ResponseEntity.ok(labelService.update(callerId(jwt), projectKey, labelId, request));
    }

    @DeleteMapping("/{labelId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey,
            @PathVariable UUID labelId) {
        labelService.delete(callerId(jwt), projectKey, labelId);
        return ResponseEntity.noContent().build();
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
