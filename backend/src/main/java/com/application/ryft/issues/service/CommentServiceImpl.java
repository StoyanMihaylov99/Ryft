package com.application.ryft.issues.service;

import com.application.ryft.identity.user.dto.UserResponse;
import com.application.ryft.identity.user.service.UserService;
import com.application.ryft.issues.dto.CommentResponse;
import com.application.ryft.issues.dto.CreateCommentRequest;
import com.application.ryft.issues.dto.UpdateCommentRequest;
import com.application.ryft.issues.entity.Comment;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.exception.CommentNotFoundException;
import com.application.ryft.issues.exception.InsufficientProjectRoleException;
import com.application.ryft.issues.exception.IssueNotFoundException;
import com.application.ryft.issues.exception.NotCommentAuthorException;
import com.application.ryft.issues.repository.CommentRepository;
import com.application.ryft.issues.repository.IssueRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final IssueProjectAccess projectAccess;
    private final UserService userService;

    public CommentServiceImpl(CommentRepository commentRepository, IssueRepository issueRepository,
            IssueProjectAccess projectAccess, UserService userService) {
        this.commentRepository = commentRepository;
        this.issueRepository = issueRepository;
        this.projectAccess = projectAccess;
        this.userService = userService;
    }

    @Override
    @Transactional
    public CommentResponse create(UUID callerId, String issueKey, CreateCommentRequest request) {
        Issue issue = requireIssue(issueKey);
        String projectKey = projectKeyOf(issue);
        projectAccess.requireMembership(callerId, projectKey);
        requireNotViewer(callerId, projectKey);

        // flush so @CreationTimestamp (VM-generated at flush time) is populated before we read it back below
        Comment comment = commentRepository.saveAndFlush(new Comment(issue, callerId, request.body().trim()));
        return toResponse(comment, userService.getById(callerId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> listForIssue(UUID callerId, String issueKey) {
        Issue issue = requireIssue(issueKey);
        projectAccess.requireMembership(callerId, projectKeyOf(issue));

        List<Comment> comments = commentRepository.findAllByIssueIdOrderByCreatedAtAsc(issue.getId());
        Map<UUID, UserResponse> authorsById = fetchAuthors(comments);
        return comments.stream()
                .map(comment -> toResponse(comment, authorsById.get(comment.getAuthorId())))
                .toList();
    }

    @Override
    @Transactional
    public CommentResponse update(UUID callerId, UUID commentId, UpdateCommentRequest request) {
        Comment comment = requireComment(commentId);
        String projectKey = projectKeyOf(comment.getIssue());
        projectAccess.requireMembership(callerId, projectKey);
        requireNotViewer(callerId, projectKey);
        requireAuthor(callerId, comment);

        comment.editBody(request.body().trim());
        return toResponse(comment, userService.getById(comment.getAuthorId()));
    }

    @Override
    @Transactional
    public void delete(UUID callerId, UUID commentId) {
        Comment comment = requireComment(commentId);
        String projectKey = projectKeyOf(comment.getIssue());
        projectAccess.requireMembership(callerId, projectKey);
        requireNotViewer(callerId, projectKey);
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

    /** Viewer is fully read-only: no commenting, not even editing/deleting a comment they posted before being downgraded. */
    private void requireNotViewer(UUID callerId, String projectKey) {
        if (projectAccess.isViewer(callerId, projectKey)) {
            throw new InsufficientProjectRoleException();
        }
    }

    /** Safe because a project key (validated at creation) never contains a hyphen — see CreateProjectRequest. */
    private String projectKeyOf(Issue issue) {
        return issue.getKey().substring(0, issue.getKey().lastIndexOf('-'));
    }

    /** One query for every distinct author in the list, instead of one per comment. */
    private Map<UUID, UserResponse> fetchAuthors(List<Comment> comments) {
        Set<UUID> authorIds = comments.stream().map(Comment::getAuthorId).collect(Collectors.toSet());
        return userService.findAllByIds(authorIds);
    }

    /** author is null when the id no longer resolves to a user; the comment still renders without attribution. */
    private CommentResponse toResponse(Comment comment, UserResponse author) {
        String authorDisplayName = author != null ? author.displayName() : null;
        String authorAvatarUrl = author != null ? author.avatarUrl() : null;
        return new CommentResponse(comment.getId(), comment.getIssue().getId(), comment.getAuthorId(),
                authorDisplayName, authorAvatarUrl, comment.getBody(), comment.getCreatedAt(),
                comment.getUpdatedAt());
    }

    private String normalizeKey(String key) {
        return key.trim().toUpperCase(Locale.ROOT);
    }
}
