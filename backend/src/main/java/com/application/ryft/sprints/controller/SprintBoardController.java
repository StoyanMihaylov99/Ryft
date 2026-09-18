package com.application.ryft.sprints.controller;

import com.application.ryft.sprints.dto.SprintBoardResponse;
import com.application.ryft.sprints.service.SprintBoardService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/board/sprint")
public class SprintBoardController {

    private final SprintBoardService sprintBoardService;

    public SprintBoardController(SprintBoardService sprintBoardService) {
        this.sprintBoardService = sprintBoardService;
    }

    @GetMapping
    public ResponseEntity<SprintBoardResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectKey) {
        return ResponseEntity.ok(sprintBoardService.getBoard(callerId(jwt), projectKey));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
