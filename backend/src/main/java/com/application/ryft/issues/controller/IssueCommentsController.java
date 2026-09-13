package com.application.ryft.issues.controller;

import com.application.ryft.issues.dto.CommentResponse;
import com.application.ryft.issues.dto.CreateCommentRequest;
import com.application.ryft.issues.service.CommentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The comment collection nested under an issue — see {@link CommentController} for edit/delete. */
@RestController
@RequestMapping("/api/v1/issues/{issueKey}/comments")
public class IssueCommentsController {

    private final CommentService commentService;

    public IssueCommentsController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public ResponseEntity<CommentResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueKey,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse comment = commentService.create(callerId(jwt), issueKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String issueKey) {
        return ResponseEntity.ok(commentService.listForIssue(callerId(jwt), issueKey));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
