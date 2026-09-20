package com.application.ryft.issues.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.identity.user.dto.UserResponse;
import com.application.ryft.identity.user.service.UserService;
import com.application.ryft.issues.dto.CommentResponse;
import com.application.ryft.issues.dto.CreateCommentRequest;
import com.application.ryft.issues.dto.UpdateCommentRequest;
import com.application.ryft.issues.entity.Comment;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.exception.CommentNotFoundException;
import com.application.ryft.issues.exception.InsufficientProjectRoleException;
import com.application.ryft.issues.exception.IssueNotFoundException;
import com.application.ryft.issues.exception.NotAProjectMemberException;
import com.application.ryft.issues.exception.NotCommentAuthorException;
import com.application.ryft.issues.repository.CommentRepository;
import com.application.ryft.issues.repository.IssueRepository;
import com.application.ryft.projects.dto.ProjectResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private IssueProjectAccess projectAccess;

    @Mock
    private UserService userService;

    private CommentServiceImpl commentService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM,
            null, callerId, 1000.0);
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);
    private final UserResponse caller = new UserResponse(callerId, "caller@example.com", "Caller Name",
            "https://example.com/avatar.png");

    @BeforeEach
    void setUp() {
        commentService = new CommentServiceImpl(commentRepository, issueRepository, projectAccess, userService);
    }

    @Test
    void createSavesCommentAuthoredByCaller() {
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(commentRepository.saveAndFlush(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.getById(callerId)).thenReturn(caller);

        CommentResponse result = commentService.create(callerId, "TRK-1", new CreateCommentRequest("Looks good"));

        assertThat(result.authorId()).isEqualTo(callerId);
        assertThat(result.authorDisplayName()).isEqualTo("Caller Name");
        assertThat(result.authorAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(result.body()).isEqualTo("Looks good");
    }

    @Test
    void createRequiresIssueToExist() {
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(callerId, "TRK-1", new CreateCommentRequest("Hi")))
                .isInstanceOf(IssueNotFoundException.class);
    }

    @Test
    void createRequiresCallerToBeAProjectMember() {
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenThrow(new NotAProjectMemberException());

        assertThatThrownBy(() -> commentService.create(callerId, "TRK-1", new CreateCommentRequest("Hi")))
                .isInstanceOf(NotAProjectMemberException.class);
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void listForIssueReturnsCommentsInOrder() {
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        Comment comment = new Comment(issue, callerId, "First");
        when(commentRepository.findAllByIssueIdOrderByCreatedAtAsc(issue.getId())).thenReturn(List.of(comment));
        when(userService.findAllByIds(Set.of(callerId))).thenReturn(Map.of(callerId, caller));

        List<CommentResponse> result = commentService.listForIssue(callerId, "TRK-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).body()).isEqualTo("First");
        assertThat(result.get(0).authorDisplayName()).isEqualTo("Caller Name");
        assertThat(result.get(0).authorAvatarUrl()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    void listForIssueOmitsAttributionWhenAuthorNoLongerResolves() {
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        Comment comment = new Comment(issue, callerId, "First");
        when(commentRepository.findAllByIssueIdOrderByCreatedAtAsc(issue.getId())).thenReturn(List.of(comment));
        when(userService.findAllByIds(Set.of(callerId))).thenReturn(Map.of());

        List<CommentResponse> result = commentService.listForIssue(callerId, "TRK-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).authorDisplayName()).isNull();
        assertThat(result.get(0).authorAvatarUrl()).isNull();
    }

    @Test
    void updateByAuthorSucceeds() {
        Comment comment = new Comment(issue, callerId, "Original");
        when(commentRepository.findById(any(UUID.class))).thenReturn(Optional.of(comment));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(userService.getById(callerId)).thenReturn(caller);

        CommentResponse result = commentService.update(callerId, UUID.randomUUID(),
                new UpdateCommentRequest("Edited"));

        assertThat(result.body()).isEqualTo("Edited");
        assertThat(result.authorDisplayName()).isEqualTo("Caller Name");
    }

    @Test
    void updateByNonAuthorIsRejected() {
        UUID authorId = UUID.randomUUID();
        Comment comment = new Comment(issue, authorId, "Original");
        when(commentRepository.findById(any(UUID.class))).thenReturn(Optional.of(comment));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);

        assertThatThrownBy(() -> commentService.update(callerId, UUID.randomUUID(), new UpdateCommentRequest("Edited")))
                .isInstanceOf(NotCommentAuthorException.class);
    }

    @Test
    void updateRequiresCommentToExist() {
        when(commentRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.update(callerId, UUID.randomUUID(), new UpdateCommentRequest("Edited")))
                .isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    void deleteByAuthorSucceeds() {
        Comment comment = new Comment(issue, callerId, "Original");
        when(commentRepository.findById(any(UUID.class))).thenReturn(Optional.of(comment));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);

        commentService.delete(callerId, UUID.randomUUID());

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteByNonAuthorIsRejected() {
        UUID authorId = UUID.randomUUID();
        Comment comment = new Comment(issue, authorId, "Original");
        when(commentRepository.findById(any(UUID.class))).thenReturn(Optional.of(comment));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);

        assertThatThrownBy(() -> commentService.delete(callerId, UUID.randomUUID()))
                .isInstanceOf(NotCommentAuthorException.class);
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    void createRejectsViewer() {
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isViewer(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> commentService.create(callerId, "TRK-1", new CreateCommentRequest("Sneaking in")))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsViewerBeforeCheckingAuthorship() {
        // The Viewer posted this comment before being downgraded — still rejected, and with the
        // insufficient-role reason, not "not the author".
        Comment comment = new Comment(issue, callerId, "Original");
        when(commentRepository.findById(any(UUID.class))).thenReturn(Optional.of(comment));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isViewer(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> commentService.update(callerId, UUID.randomUUID(), new UpdateCommentRequest("Edited")))
                .isInstanceOf(InsufficientProjectRoleException.class);
    }

    @Test
    void deleteRejectsViewerBeforeCheckingAuthorship() {
        Comment comment = new Comment(issue, callerId, "Original");
        when(commentRepository.findById(any(UUID.class))).thenReturn(Optional.of(comment));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isViewer(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> commentService.delete(callerId, UUID.randomUUID()))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(commentRepository, never()).delete(any(Comment.class));
    }
}
