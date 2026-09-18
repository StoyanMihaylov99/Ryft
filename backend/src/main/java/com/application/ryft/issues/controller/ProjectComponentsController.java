package com.application.ryft.issues.controller;

import com.application.ryft.issues.dto.ComponentResponse;
import com.application.ryft.issues.dto.CreateComponentRequest;
import com.application.ryft.issues.dto.UpdateComponentRequest;
import com.application.ryft.issues.service.ComponentService;
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
@RequestMapping("/api/v1/projects/{projectKey}/components")
public class ProjectComponentsController {

    private final ComponentService componentService;

    public ProjectComponentsController(ComponentService componentService) {
        this.componentService = componentService;
    }

    @PostMapping
    public ResponseEntity<ComponentResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey,
            @Valid @RequestBody CreateComponentRequest request) {
        ComponentResponse component = componentService.create(callerId(jwt), projectKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(component);
    }

    @GetMapping
    public ResponseEntity<List<ComponentResponse>> list(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String projectKey) {
        return ResponseEntity.ok(componentService.listForProject(callerId(jwt), projectKey));
    }

    @PatchMapping("/{componentId}")
    public ResponseEntity<ComponentResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey,
            @PathVariable UUID componentId, @Valid @RequestBody UpdateComponentRequest request) {
        return ResponseEntity.ok(componentService.update(callerId(jwt), projectKey, componentId, request));
    }

    @DeleteMapping("/{componentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey,
            @PathVariable UUID componentId) {
        componentService.delete(callerId(jwt), projectKey, componentId);
        return ResponseEntity.noContent().build();
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
