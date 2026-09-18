package com.application.ryft.issues.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.issues.dto.ChangeIssueStatusRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.CreateSubtaskRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.UpdateIssueRequest;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssueKeySequence;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.exception.AssigneeNotAProjectMemberException;
import com.application.ryft.issues.exception.InsufficientProjectRoleException;
import com.application.ryft.issues.exception.InvalidParentLinkException;
import com.application.ryft.issues.exception.IssueNotFoundException;
import com.application.ryft.issues.exception.NotAProjectMemberException;
import com.application.ryft.issues.repository.CommentRepository;
import com.application.ryft.issues.repository.IssueKeySequenceRepository;
import com.application.ryft.issues.repository.IssueRepository;
import com.application.ryft.projects.dto.ProjectResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private IssueKeySequenceRepository issueKeySequenceRepository;

    @Mock
    private IssueProjectAccess projectAccess;

    @Mock
    private CommentRepository commentRepository;

    private IssueServiceImpl issueService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);

    @BeforeEach
    void setUp() {
        issueService = new IssueServiceImpl(issueRepository, issueKeySequenceRepository, projectAccess,
                commentRepository);
    }

    @Test
    void createGeneratesKeyAndDefaultsPriorityAndStatus() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.BUG, "Fix login", null, null, null, null, null));

        assertThat(result.key()).isEqualTo("TRK-1");
        assertThat(result.status()).isEqualTo(IssueStatus.TODO);
        assertThat(result.priority()).isEqualTo(IssuePriority.MEDIUM);
        assertThat(result.reporterId()).isEqualTo(callerId);
        assertThat(result.storyPoints()).isNull();
    }

    @Test
    void createSetsStoryPointsWhenProvided() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.BUG, "Fix login", null, null, null, 5, null));

        assertThat(result.storyPoints()).isEqualTo(5);
    }

    @Test
    void createUsesSecondSequenceNumberWhenSequenceExists() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        IssueKeySequence existing = new IssueKeySequence(projectId);
        existing.incrementAndGet();
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.of(existing));
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Second issue", null, IssuePriority.HIGH, null, null, null));

        assertThat(result.key()).isEqualTo("TRK-2");
        assertThat(result.priority()).isEqualTo(IssuePriority.HIGH);
    }

    @Test
    void createPropagatesProjectNotFound() {
        when(projectAccess.requireMembership(callerId, "TRK"))
                .thenThrow(new com.application.ryft.issues.exception.ProjectNotFoundException("TRK"));

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null)))
                .isInstanceOf(com.application.ryft.issues.exception.ProjectNotFoundException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createPropagatesNotAProjectMember() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenThrow(new NotAProjectMemberException());

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null)))
                .isInstanceOf(NotAProjectMemberException.class);
    }

    @Test
    void createRejectsCallerWhoIsNotOwnerOrAdmin() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null)))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createRejectsAssigneeNotAProjectMember() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        UUID outsiderId = UUID.randomUUID();
        when(projectAccess.isMember(callerId, "TRK", outsiderId)).thenReturn(false);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, outsiderId, null, null)))
                .isInstanceOf(AssigneeNotAProjectMemberException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createAcceptsAssigneeThatIsAProjectMember() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        UUID assigneeId = UUID.randomUUID();
        when(projectAccess.isMember(callerId, "TRK", assigneeId)).thenReturn(true);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, assigneeId, null, null));

        assertThat(result.assigneeId()).isEqualTo(assigneeId);
    }

    @Test
    void getRequiresIssueToExist() {
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> issueService.get(callerId, "trk-1"))
                .isInstanceOf(IssueNotFoundException.class);
    }

    @Test
    void getRequiresCallerToBeAProjectMember() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenThrow(new NotAProjectMemberException());

        assertThatThrownBy(() -> issueService.get(callerId, "TRK-1"))
                .isInstanceOf(NotAProjectMemberException.class);
    }

    @Test
    void updateAppliesProvidedFieldsOnly() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Original", "orig desc", IssuePriority.LOW, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        IssueResponse result = issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest("New title", null, IssuePriority.HIGH, null, null, null));

        assertThat(result.title()).isEqualTo("New title");
        assertThat(result.description()).isEqualTo("orig desc");
        assertThat(result.priority()).isEqualTo(IssuePriority.HIGH);
    }

    @Test
    void updateSetsStoryPointsWhenProvided() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Original", "orig desc", IssuePriority.LOW, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        IssueResponse result = issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest(null, null, null, null, 8, null));

        assertThat(result.storyPoints()).isEqualTo(8);
    }

    @Test
    void updateWithNullStoryPointsLeavesExistingValueUnchanged() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Original", "orig desc", IssuePriority.LOW, null,
                callerId, 1000.0);
        issue.setStoryPoints(3);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        IssueResponse result = issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest("New title", null, null, null, null, null));

        assertThat(result.storyPoints()).isEqualTo(3);
    }

    @Test
    void updateRejectsCallerWhoIsNotOwnerOrAdmin() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest("New title", null, null, null, null, null)))
                .isInstanceOf(InsufficientProjectRoleException.class);
    }

    @Test
    void updateRejectsAssigneeNotAProjectMember() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        UUID outsiderId = UUID.randomUUID();
        when(projectAccess.isMember(callerId, "TRK", outsiderId)).thenReturn(false);

        assertThatThrownBy(() -> issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest(null, null, null, outsiderId, null, null)))
                .isInstanceOf(AssigneeNotAProjectMemberException.class);
    }

    @Test
    void changeStatusToDoneSetsResolvedAt() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);

        IssueResponse result = issueService.changeStatus(callerId, "TRK-1",
                new ChangeIssueStatusRequest(IssueStatus.DONE));

        assertThat(result.status()).isEqualTo(IssueStatus.DONE);
        assertThat(result.resolvedAt()).isNotNull();
        // Unlike create/update/delete, dragging a card is open to any project member — no role check.
        verify(projectAccess, never()).isOwnerOrAdmin(any(), any());
    }

    @Test
    void changeStatusAwayFromDoneClearsResolvedAt() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        issue.setStatus(IssueStatus.DONE);
        issue.setResolvedAt(Instant.now());
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);

        IssueResponse result = issueService.changeStatus(callerId, "TRK-1",
                new ChangeIssueStatusRequest(IssueStatus.IN_PROGRESS));

        assertThat(result.resolvedAt()).isNull();
    }

    @Test
    void changeStatusRequiresCallerToBeAProjectMember() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenThrow(new NotAProjectMemberException());

        assertThatThrownBy(() -> issueService.changeStatus(callerId, "TRK-1",
                new ChangeIssueStatusRequest(IssueStatus.DONE)))
                .isInstanceOf(NotAProjectMemberException.class);
    }

    @Test
    void deleteRemovesTheIssue() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        issueService.delete(callerId, "TRK-1");

        ArgumentCaptor<Issue> captor = ArgumentCaptor.forClass(Issue.class);
        verify(issueRepository).delete(captor.capture());
        assertThat(captor.getValue().getKey()).isEqualTo("TRK-1");
        verify(commentRepository).deleteAllByIssueId(issue.getId());
    }

    @Test
    void deleteRejectsCallerWhoIsNotOwnerOrAdmin() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> issueService.delete(callerId, "TRK-1"))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(issueRepository, never()).delete(any());
        verify(commentRepository, never()).deleteAllByIssueId(any());
    }

    @Test
    void listForProjectReturnsProjectsIssues() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        Issue issue = new Issue(projectId, "TRK-1", IssueType.STORY, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(issueRepository.findAllByProjectIdAndTypeNotOrderByCreatedAtAsc(projectId, IssueType.SUBTASK))
                .thenReturn(List.of(issue));

        List<IssueResponse> result = issueService.listForProject(callerId, "TRK");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).key()).isEqualTo("TRK-1");
    }

    @Test
    void listForProjectFilteredBySprintDelegatesToSprintScopedQuery() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        UUID sprintId = UUID.randomUUID();
        Issue issue = new Issue(projectId, "TRK-1", IssueType.STORY, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        issue.setSprintId(sprintId);
        when(issueRepository.findAllByProjectIdAndSprintIdAndTypeNotOrderByCreatedAtAsc(projectId, sprintId,
                IssueType.SUBTASK)).thenReturn(List.of(issue));

        List<IssueResponse> result = issueService.listForProject(callerId, "TRK", sprintId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sprintId()).isEqualTo(sprintId);
    }

    @Test
    void listBacklogForProjectReturnsIssuesWithNoSprintOrderedByRank() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        Issue issue = new Issue(projectId, "TRK-1", IssueType.STORY, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findAllByProjectIdAndSprintIdIsNullAndTypeNotOrderByBacklogRankAsc(projectId,
                IssueType.SUBTASK)).thenReturn(List.of(issue));

        List<IssueResponse> result = issueService.listBacklogForProject(callerId, "TRK");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sprintId()).isNull();
    }

    @Test
    void listForSprintReturnsAllIssuesRegardlessOfStatus() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        UUID sprintId = UUID.randomUUID();
        Issue issue = new Issue(projectId, "TRK-1", IssueType.STORY, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        issue.setSprintId(sprintId);
        issue.setStatus(IssueStatus.DONE);
        when(issueRepository.findAllByProjectIdAndSprintIdAndTypeNotOrderByCreatedAtAsc(projectId, sprintId,
                IssueType.SUBTASK)).thenReturn(List.of(issue));

        List<IssueResponse> result = issueService.listForSprint(callerId, "TRK", sprintId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(IssueStatus.DONE);
    }

    @Test
    void listForProjectExcludesSubtasksFromTheQuery() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(issueRepository.findAllByProjectIdAndTypeNotOrderByCreatedAtAsc(projectId, IssueType.SUBTASK))
                .thenReturn(List.of());

        issueService.listForProject(callerId, "TRK");

        verify(issueRepository).findAllByProjectIdAndTypeNotOrderByCreatedAtAsc(projectId, IssueType.SUBTASK);
    }

    @Test
    void listBacklogForProjectExcludesSubtasksFromTheQuery() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(issueRepository.findAllByProjectIdAndSprintIdIsNullAndTypeNotOrderByBacklogRankAsc(projectId,
                IssueType.SUBTASK)).thenReturn(List.of());

        issueService.listBacklogForProject(callerId, "TRK");

        verify(issueRepository).findAllByProjectIdAndSprintIdIsNullAndTypeNotOrderByBacklogRankAsc(projectId,
                IssueType.SUBTASK);
    }

    @Test
    void listForSprintExcludesSubtasksFromTheQuery() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        UUID sprintId = UUID.randomUUID();
        when(issueRepository.findAllByProjectIdAndSprintIdAndTypeNotOrderByCreatedAtAsc(projectId, sprintId,
                IssueType.SUBTASK)).thenReturn(List.of());

        issueService.listForSprint(callerId, "TRK", sprintId);

        verify(issueRepository).findAllByProjectIdAndSprintIdAndTypeNotOrderByCreatedAtAsc(projectId, sprintId,
                IssueType.SUBTASK);
    }

    @Test
    void createAssignsFirstBacklogRankWhenProjectHasNoOtherIssues() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.findFirstByProjectIdOrderByBacklogRankDesc(projectId)).thenReturn(Optional.empty());
        ArgumentCaptor<Issue> captor = ArgumentCaptor.forClass(Issue.class);
        when(issueRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        issueService.create(callerId, "TRK", new CreateIssueRequest(IssueType.BUG, "First", null, null, null, null, null));

        assertThat(captor.getValue().getBacklogRank()).isEqualTo(1000.0);
    }

    @Test
    void createAssignsSecondBacklogRankAfterTheExistingHighestRank() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        Issue existing = new Issue(projectId, "TRK-1", IssueType.BUG, "First", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findFirstByProjectIdOrderByBacklogRankDesc(projectId)).thenReturn(Optional.of(existing));
        ArgumentCaptor<Issue> captor = ArgumentCaptor.forClass(Issue.class);
        when(issueRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        issueService.create(callerId, "TRK", new CreateIssueRequest(IssueType.BUG, "Second", null, null, null, null, null));

        assertThat(captor.getValue().getBacklogRank()).isEqualTo(2000.0);
    }

    @Test
    void moveToSprintAssignsSprint() {
        UUID sprintId = UUID.randomUUID();
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        IssueResponse result = issueService.moveToSprint(callerId, "TRK-1", sprintId);

        assertThat(result.sprintId()).isEqualTo(sprintId);
    }

    @Test
    void moveToSprintWithNullSprintIdClearsSprint() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        issue.setSprintId(UUID.randomUUID());
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        IssueResponse result = issueService.moveToSprint(callerId, "TRK-1", null);

        assertThat(result.sprintId()).isNull();
    }

    @Test
    void moveToSprintRejectsCallerWhoIsNotOwnerOrAdmin() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> issueService.moveToSprint(callerId, "TRK-1", UUID.randomUUID()))
                .isInstanceOf(InsufficientProjectRoleException.class);
        assertThat(issue.getSprintId()).isNull();
    }

    @Test
    void reorderBacklogWithBothNeighborsUsesMidpoint() {
        Issue issue = new Issue(projectId, "TRK-3", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 3000.0);
        Issue before = new Issue(projectId, "TRK-1", IssueType.BUG, "Before", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        Issue after = new Issue(projectId, "TRK-2", IssueType.BUG, "After", null, IssuePriority.MEDIUM, null,
                callerId, 2000.0);
        when(issueRepository.findByKey("TRK-3")).thenReturn(Optional.of(issue));
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(before));
        when(issueRepository.findByKey("TRK-2")).thenReturn(Optional.of(after));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        IssueResponse result = issueService.reorderBacklog(callerId, "TRK-3", "TRK-1", "TRK-2");

        assertThat(result).isNotNull();
        assertThat(issue.getBacklogRank()).isEqualTo(1500.0);
    }

    @Test
    void reorderBacklogWithOnlyBeforeStepsPastIt() {
        Issue issue = new Issue(projectId, "TRK-2", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 500.0);
        Issue before = new Issue(projectId, "TRK-1", IssueType.BUG, "Before", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-2")).thenReturn(Optional.of(issue));
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(before));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        issueService.reorderBacklog(callerId, "TRK-2", "TRK-1", null);

        assertThat(issue.getBacklogRank()).isEqualTo(2000.0);
    }

    @Test
    void reorderBacklogWithOnlyAfterStepsBeforeIt() {
        Issue issue = new Issue(projectId, "TRK-2", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 5000.0);
        Issue after = new Issue(projectId, "TRK-1", IssueType.BUG, "After", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-2")).thenReturn(Optional.of(issue));
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(after));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        issueService.reorderBacklog(callerId, "TRK-2", null, "TRK-1");

        assertThat(issue.getBacklogRank()).isEqualTo(0.0);
    }

    @Test
    void reorderBacklogWithNeitherNeighborLeavesRankUnchanged() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        issueService.reorderBacklog(callerId, "TRK-1", null, null);

        assertThat(issue.getBacklogRank()).isEqualTo(1000.0);
    }

    @Test
    void reorderBacklogRejectsCallerWhoIsNotOwnerOrAdmin() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> issueService.reorderBacklog(callerId, "TRK-1", null, null))
                .isInstanceOf(InsufficientProjectRoleException.class);
        assertThat(issue.getBacklogRank()).isEqualTo(1000.0);
    }

    @Test
    void createStoryWithValidParentLinksToEpic() {
        UUID epicId = UUID.randomUUID();
        Issue epic = new Issue(projectId, "TRK-1", IssueType.EPIC, "Epic", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(epicId, projectId)).thenReturn(Optional.of(epic));
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.STORY, "Story", null, null, null, null, epicId));

        assertThat(result.parentId()).isEqualTo(epicId);
    }

    @Test
    void createWithParentPointingToNonEpicThrows() {
        UUID parentId = UUID.randomUUID();
        Issue nonEpicParent = new Issue(projectId, "TRK-1", IssueType.TASK, "Not an epic", null,
                IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.of(nonEpicParent));

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.STORY, "Story", null, null, null, null, parentId)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createWithParentInDifferentProjectThrows() {
        UUID parentId = UUID.randomUUID();
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.empty());
        when(issueRepository.existsById(parentId)).thenReturn(true);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.STORY, "Story", null, null, null, null, parentId)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createWithUnknownParentThrows() {
        UUID parentId = UUID.randomUUID();
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.empty());
        when(issueRepository.existsById(parentId)).thenReturn(false);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.STORY, "Story", null, null, null, null, parentId)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createEpicWithNonNullParentThrows() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.EPIC, "Epic", null, null, null, null, UUID.randomUUID())))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void updateAddsParentLinkToEpic() {
        UUID epicId = UUID.randomUUID();
        Issue issue = new Issue(projectId, "TRK-2", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        Issue epic = new Issue(projectId, "TRK-1", IssueType.EPIC, "Epic", null, IssuePriority.MEDIUM, null,
                callerId, 500.0);
        when(issueRepository.findByKey("TRK-2")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(epicId, projectId)).thenReturn(Optional.of(epic));

        IssueResponse result = issueService.update(callerId, "TRK-2",
                new UpdateIssueRequest(null, null, null, null, null, epicId));

        assertThat(result.parentId()).isEqualTo(epicId);
    }

    @Test
    void updateWithParentPointingToNonEpicThrows() {
        UUID parentId = UUID.randomUUID();
        Issue issue = new Issue(projectId, "TRK-2", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        Issue nonEpicParent = new Issue(projectId, "TRK-1", IssueType.TASK, "Not an epic", null,
                IssuePriority.MEDIUM, null, callerId, 500.0);
        when(issueRepository.findByKey("TRK-2")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.of(nonEpicParent));

        assertThatThrownBy(() -> issueService.update(callerId, "TRK-2",
                new UpdateIssueRequest(null, null, null, null, null, parentId)))
                .isInstanceOf(InvalidParentLinkException.class);
        assertThat(issue.getParentIssueId()).isNull();
    }

    @Test
    void updateOnAnEpicWithParentThrows() {
        Issue epic = new Issue(projectId, "TRK-1", IssueType.EPIC, "Epic", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(epic));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest(null, null, null, null, null, UUID.randomUUID())))
                .isInstanceOf(InvalidParentLinkException.class);
    }

    @Test
    void listForProjectByEpicReturnsOnlyMatchingIssues() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        UUID epicId = UUID.randomUUID();
        Issue issue = new Issue(projectId, "TRK-2", IssueType.STORY, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        issue.setParentIssueId(epicId);
        when(issueRepository.findAllByProjectIdAndParentIssueIdOrderByCreatedAtAsc(projectId, epicId))
                .thenReturn(List.of(issue));

        List<IssueResponse> result = issueService.listForProjectByEpic(callerId, "TRK", epicId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).parentId()).isEqualTo(epicId);
    }

    @Test
    void moveUnfinishedIssuesToBacklogClearsSprintOnlyOnNonDoneIssues() {
        UUID sprintId = UUID.randomUUID();
        Issue unfinished = new Issue(projectId, "TRK-1", IssueType.BUG, "Unfinished", null, IssuePriority.MEDIUM,
                null, callerId, 1000.0);
        unfinished.setSprintId(sprintId);
        Issue done = new Issue(projectId, "TRK-2", IssueType.BUG, "Done", null, IssuePriority.MEDIUM, null, callerId,
                2000.0);
        done.setSprintId(sprintId);
        done.setStatus(IssueStatus.DONE);
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(issueRepository.findAllByProjectIdAndSprintId(projectId, sprintId)).thenReturn(List.of(unfinished, done));

        issueService.moveUnfinishedIssuesToBacklog(callerId, "TRK", sprintId);

        assertThat(unfinished.getSprintId()).isNull();
        assertThat(done.getSprintId()).isEqualTo(sprintId);
        verify(projectAccess, never()).isOwnerOrAdmin(any(), any());
    }

    @Test
    void createSubtaskTypeWithoutParentIdThrows() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.SUBTASK, "Subtask", null, null, null, null, null)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createSubtaskTypeWithEpicParentThrows() {
        UUID parentId = UUID.randomUUID();
        Issue epicParent = new Issue(projectId, "TRK-1", IssueType.EPIC, "Epic", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.of(epicParent));

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.SUBTASK, "Subtask", null, null, null, null, parentId)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createSubtaskTypeWithParentInDifferentProjectThrows() {
        UUID parentId = UUID.randomUUID();
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.empty());
        when(issueRepository.existsById(parentId)).thenReturn(true);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.SUBTASK, "Subtask", null, null, null, null, parentId)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createSubtaskTypeWithStoryParentSucceeds() {
        UUID parentId = UUID.randomUUID();
        Issue storyParent = new Issue(projectId, "TRK-1", IssueType.STORY, "Story", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.of(storyParent));
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.SUBTASK, "Subtask", null, null, null, null, parentId));

        assertThat(result.type()).isEqualTo(IssueType.SUBTASK);
        assertThat(result.parentId()).isEqualTo(parentId);
    }

    @Test
    void updateSubtaskParentToNonStoryTaskOrBugThrows() {
        Issue subtask = new Issue(projectId, "TRK-2", IssueType.SUBTASK, "Subtask", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        UUID epicId = UUID.randomUUID();
        Issue epic = new Issue(projectId, "TRK-1", IssueType.EPIC, "Epic", null, IssuePriority.MEDIUM, null, callerId,
                500.0);
        when(issueRepository.findByKey("TRK-2")).thenReturn(Optional.of(subtask));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(epicId, projectId)).thenReturn(Optional.of(epic));

        assertThatThrownBy(() -> issueService.update(callerId, "TRK-2",
                new UpdateIssueRequest(null, null, null, null, null, epicId)))
                .isInstanceOf(InvalidParentLinkException.class);
        assertThat(subtask.getParentIssueId()).isNull();
    }

    @Test
    void updateSubtaskParentToAnotherStoryTaskOrBugSucceeds() {
        Issue subtask = new Issue(projectId, "TRK-2", IssueType.SUBTASK, "Subtask", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        UUID newParentId = UUID.randomUUID();
        Issue newParent = new Issue(projectId, "TRK-3", IssueType.TASK, "Task", null, IssuePriority.MEDIUM, null,
                callerId, 500.0);
        when(issueRepository.findByKey("TRK-2")).thenReturn(Optional.of(subtask));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(newParentId, projectId)).thenReturn(Optional.of(newParent));

        IssueResponse result = issueService.update(callerId, "TRK-2",
                new UpdateIssueRequest(null, null, null, null, null, newParentId));

        assertThat(result.parentId()).isEqualTo(newParentId);
    }

    @Test
    void createSubtaskRejectsEpicParent() {
        Issue parent = new Issue(projectId, "TRK-1", IssueType.EPIC, "Epic", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(parent));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> issueService.createSubtask(callerId, "TRK-1",
                new CreateSubtaskRequest("Checklist item", null, null, null)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createSubtaskRejectsSubtaskParent() {
        Issue parent = new Issue(projectId, "TRK-1", IssueType.SUBTASK, "Existing subtask", null,
                IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(parent));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> issueService.createSubtask(callerId, "TRK-1",
                new CreateSubtaskRequest("Nested", null, null, null)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createSubtaskRejectsCallerWhoIsNotOwnerOrAdmin() {
        Issue parent = new Issue(projectId, "TRK-1", IssueType.STORY, "Parent", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(parent));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> issueService.createSubtask(callerId, "TRK-1",
                new CreateSubtaskRequest("Checklist item", null, null, null)))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createSubtaskRejectsAssigneeNotAProjectMember() {
        Issue parent = new Issue(projectId, "TRK-1", IssueType.STORY, "Parent", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(parent));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        UUID outsiderId = UUID.randomUUID();
        when(projectAccess.isMember(callerId, "TRK", outsiderId)).thenReturn(false);

        assertThatThrownBy(() -> issueService.createSubtask(callerId, "TRK-1",
                new CreateSubtaskRequest("Checklist item", null, null, outsiderId)))
                .isInstanceOf(AssigneeNotAProjectMemberException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createSubtaskUnderStoryCreatesLinkedSubtask() {
        Issue parent = new Issue(projectId, "TRK-1", IssueType.STORY, "Parent story", null, IssuePriority.MEDIUM,
                null, callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(parent));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.findFirstByProjectIdOrderByBacklogRankDesc(projectId)).thenReturn(Optional.empty());
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.createSubtask(callerId, "TRK-1",
                new CreateSubtaskRequest("Checklist item", null, null, null));

        assertThat(result.type()).isEqualTo(IssueType.SUBTASK);
        assertThat(result.parentId()).isEqualTo(parent.getId());
        assertThat(result.priority()).isEqualTo(IssuePriority.MEDIUM);
    }

    @Test
    void listSubtasksReturnsChildrenOfTheGivenIssue() {
        Issue parent = new Issue(projectId, "TRK-1", IssueType.STORY, "Parent", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(parent));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        Issue subtask = new Issue(projectId, "TRK-2", IssueType.SUBTASK, "Checklist item", null, IssuePriority.MEDIUM,
                null, callerId, 2000.0);
        subtask.setParentIssueId(parent.getId());
        when(issueRepository.findAllByParentIssueIdOrderByCreatedAtAsc(parent.getId())).thenReturn(List.of(subtask));

        List<IssueResponse> result = issueService.listSubtasks(callerId, "TRK-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(IssueType.SUBTASK);
        assertThat(result.get(0).parentId()).isEqualTo(parent.getId());
    }

    @Test
    void deleteCascadesToSubtasksAndTheirComments() {
        Issue parent = new Issue(projectId, "TRK-1", IssueType.STORY, "Parent", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        org.springframework.test.util.ReflectionTestUtils.setField(parent, "id", UUID.randomUUID());
        Issue subtask = new Issue(projectId, "TRK-2", IssueType.SUBTASK, "Checklist item", null, IssuePriority.MEDIUM,
                null, callerId, 2000.0);
        org.springframework.test.util.ReflectionTestUtils.setField(subtask, "id", UUID.randomUUID());
        subtask.setParentIssueId(parent.getId());
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(parent));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findAllByParentIssueIdOrderByCreatedAtAsc(parent.getId())).thenReturn(List.of(subtask));

        issueService.delete(callerId, "TRK-1");

        verify(commentRepository).deleteAllByIssueId(subtask.getId());
        verify(commentRepository).deleteAllByIssueId(parent.getId());
        verify(issueRepository).deleteAll(List.of(subtask));
        verify(issueRepository).delete(parent);
    }

    @Test
    void deleteOfEpicUnlinksLinkedIssuesInsteadOfDeletingThem() {
        Issue epic = new Issue(projectId, "TRK-1", IssueType.EPIC, "Epic", null, IssuePriority.MEDIUM, null, callerId,
                1000.0);
        org.springframework.test.util.ReflectionTestUtils.setField(epic, "id", UUID.randomUUID());
        Issue linkedStory = new Issue(projectId, "TRK-2", IssueType.STORY, "Story", null, IssuePriority.MEDIUM, null,
                callerId, 2000.0);
        org.springframework.test.util.ReflectionTestUtils.setField(linkedStory, "id", UUID.randomUUID());
        linkedStory.setParentIssueId(epic.getId());
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(epic));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findAllByProjectIdAndParentIssueIdOrderByCreatedAtAsc(projectId, epic.getId()))
                .thenReturn(List.of(linkedStory));

        issueService.delete(callerId, "TRK-1");

        assertThat(linkedStory.getParentIssueId()).isNull();
        verify(issueRepository, never()).delete(linkedStory);
        verify(issueRepository, never()).deleteAll(any());
        verify(issueRepository).delete(epic);
        verify(commentRepository).deleteAllByIssueId(epic.getId());
        verify(commentRepository, never()).deleteAllByIssueId(linkedStory.getId());
    }
}
