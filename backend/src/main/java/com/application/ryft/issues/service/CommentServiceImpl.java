package com.application.ryft.issues.service;

import com.application.ryft.issues.dto.CommentResponse;
import com.application.ryft.issues.dto.CreateCommentRequest;
import com.application.ryft.issues.dto.UpdateCommentRequest;
import com.application.ryft.issues.entity.Comment;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.exception.CommentNotFoundException;
import com.application.ryft.issues.exception.IssueNotFoundException;
import com.application.ryft.issues.exception.NotCommentAuthorException;
import com.application.ryft.issues.repository.CommentRepository;
import com.application.ryft.issues.repository.IssueRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final ProjectAccess projectAccess;

    public CommentServiceImpl(CommentRepository commentRepository, IssueRepository issueRepository,
            ProjectAccess projectAccess) {
        this.commentRepository = commentRepository;
        this.issueRepository = issueRepository;
        this.projectAccess = projectAccess;
    }

    @Override
    @Transactional
    public CommentResponse create(UUID callerId, String issueKey, CreateCommentRequest request) {
        Issue issue = requireIssue(issueKey);
        projectAccess.requireMembership(callerId, projectKeyOf(issue));

        Comment comment = commentRepository.save(new Comment(issue, callerId, request.body().trim()));
        return toResponse(comment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> listForIssue(UUID callerId, String issueKey) {
        Issue issue = requireIssue(issueKey);
        projectAccess.requireMembership(callerId, projectKeyOf(issue));

        return commentRepository.findAllByIssueIdOrderByCreatedAtAsc(issue.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CommentResponse update(UUID callerId, UUID commentId, UpdateCommentRequest request) {
        Comment comment = requireComment(commentId);
        projectAccess.requireMembership(callerId, projectKeyOf(comment.getIssue()));
        requireAuthor(callerId, comment);

        comment.setBody(request.body().trim());
        return toResponse(comment);
    }

    @Override
    @Transactional
    public void delete(UUID callerId, UUID commentId) {
        Comment comment = requireComment(commentId);
        projectAccess.requireMembership(callerId, projectKeyOf(comment.getIssue()));
        requireAuthor(callerId, comment);

        commentRepository.delete(comment);
    }

    private Issue requireIssue(String issueKey) {
        return issueRepository.findByKey(normalizeKey(issueKey))
                .orElseThrow(() -> new IssueNotFoundException(issueKey));
    }

    private Comment requireComment(UUID commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(CommentNotFoundException::new);
    }

    private void requireAuthor(UUID callerId, Comment comment) {
        if (!comment.getAuthorId().equals(callerId)) {
            throw new NotCommentAuthorException();
        }
    }

    /** Safe because a project key (validated at creation) never contains a hyphen — see CreateProjectRequest. */
    private String projectKeyOf(Issue issue) {
        return issue.getKey().substring(0, issue.getKey().lastIndexOf('-'));
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(comment.getId(), comment.getIssue().getId(), comment.getAuthorId(),
                comment.getBody(), comment.getCreatedAt(), comment.getUpdatedAt());
    }

    private String normalizeKey(String key) {
        return key.trim().toUpperCase(Locale.ROOT);
    }
}
