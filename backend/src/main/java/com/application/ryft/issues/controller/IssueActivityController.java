package com.application.ryft.issues.controller;

import com.application.ryft.activity.dto.ActivityEventResponse;
import com.application.ryft.activity.service.ActivityService;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.service.IssueService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lives in {@code issues}, not {@code activity} — {@link ActivityService} is a dependency-free leaf
 * that only ever takes ids/primitives, so resolving a human-readable {@code issueKey} into a
 * project/issue id pair (and enforcing project membership along the way, via
 * {@link IssueService#get}) is this module's job, not activity's.
 */
@RestController
@RequestMapping("/api/v1/issues")
public class IssueActivityController {

    private final IssueService issueService;
    private final ActivityService activityService;

    public IssueActivityController(IssueService issueService, ActivityService activityService) {
        this.issueService = issueService;
        this.activityService = activityService;
    }

    @GetMapping("/{issueKey}/activity")
    public ResponseEntity<List<ActivityEventResponse>> listForIssue(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String issueKey) {
        IssueResponse issue = issueService.get(callerId(jwt), issueKey);
        return ResponseEntity.ok(activityService.listForIssue(issue.projectId(), issue.id()));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
