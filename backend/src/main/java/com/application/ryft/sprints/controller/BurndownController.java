package com.application.ryft.sprints.controller;

import com.application.ryft.sprints.dto.BurndownResponse;
import com.application.ryft.sprints.service.BurndownService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A read/reporting endpoint — any project member may view it, unlike the Owner/Admin-gated sprint
 * management actions on {@link SprintController}. */
@RestController
@RequestMapping("/api/v1/sprints")
public class BurndownController {

    private final BurndownService burndownService;

    public BurndownController(BurndownService burndownService) {
        this.burndownService = burndownService;
    }

    @GetMapping("/{sprintId}/burndown")
    public ResponseEntity<BurndownResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sprintId) {
        return ResponseEntity.ok(burndownService.getBurndown(callerId(jwt), sprintId));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
