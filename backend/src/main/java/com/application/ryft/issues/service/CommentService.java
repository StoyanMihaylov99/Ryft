package com.application.ryft.issues.service;

import com.application.ryft.issues.dto.CommentResponse;
import com.application.ryft.issues.dto.CreateCommentRequest;
import com.application.ryft.issues.dto.UpdateCommentRequest;
import java.util.List;
import java.util.UUID;

public interface CommentService {

    CommentResponse create(UUID callerId, String issueKey, CreateCommentRequest request);

    List<CommentResponse> listForIssue(UUID callerId, String issueKey);

    /** Only the comment's own author may edit it. */
    CommentResponse update(UUID callerId, UUID commentId, UpdateCommentRequest request);

    /** Only the comment's own author may delete it. */
    void delete(UUID callerId, UUID commentId);
}
