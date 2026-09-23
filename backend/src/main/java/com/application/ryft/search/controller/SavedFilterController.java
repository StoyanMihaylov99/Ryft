package com.application.ryft.search.controller;

import com.application.ryft.search.dto.CreateSavedFilterRequest;
import com.application.ryft.search.dto.SavedFilterResponse;
import com.application.ryft.search.service.SavedFilterService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Saved, optionally project-shared searches — see {@link ProjectSearchController} for the underlying filter shape. */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/filters")
public class SavedFilterController {

    private final SavedFilterService savedFilterService;

    public SavedFilterController(SavedFilterService savedFilterService) {
        this.savedFilterService = savedFilterService;
    }

    @GetMapping
    public ResponseEntity<List<SavedFilterResponse>> list(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String projectKey) {
        return ResponseEntity.ok(savedFilterService.list(callerId(jwt), projectKey));
    }

    @PostMapping
    public ResponseEntity<SavedFilterResponse> create(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String projectKey, @Valid @RequestBody CreateSavedFilterRequest request) {
        SavedFilterResponse filter = savedFilterService.create(callerId(jwt), projectKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(filter);
    }

    @DeleteMapping("/{filterId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey,
            @PathVariable UUID filterId) {
        savedFilterService.delete(callerId(jwt), projectKey, filterId);
        return ResponseEntity.noContent().build();
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
