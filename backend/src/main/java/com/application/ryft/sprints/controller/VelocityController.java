package com.application.ryft.sprints.controller;

import com.application.ryft.sprints.dto.VelocityResponse;
import com.application.ryft.sprints.service.VelocityService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A read/reporting endpoint — any project member may view it, same gate as {@link BurndownController}.
 * {@code @Validated} at the class level is what makes Spring enforce {@code @Min}/{@code @Max} on a plain
 * {@code @RequestParam} (unlike {@code @Valid} on a {@code @RequestBody}, method-parameter constraints are
 * inert without it); no other controller in this codebase validates a query param yet, so this follows the
 * standard Spring MVC idiom rather than an existing in-repo precedent. */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/velocity")
@Validated
public class VelocityController {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 20;

    private final VelocityService velocityService;

    public VelocityController(VelocityService velocityService) {
        this.velocityService = velocityService;
    }

    @GetMapping
    public ResponseEntity<VelocityResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(MIN_LIMIT) @Max(MAX_LIMIT) int limit) {
        return ResponseEntity.ok(velocityService.getVelocity(callerId(jwt), projectKey, limit));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
