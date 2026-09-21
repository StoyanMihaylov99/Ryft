package com.application.ryft.issues.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.common.event.IssueAssigneeChangedEvent;
import com.application.ryft.common.event.IssueCreatedEvent;
import com.application.ryft.common.event.IssueStatusChangedEvent;
import com.application.ryft.issues.dto.ChangeIssueStatusRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.CreateSubtaskRequest;
import com.application.ryft.issues.dto.EpicProgressResponse;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.UpdateIssueRequest;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssueKeySequence;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.entity.Component;
import com.application.ryft.issues.entity.Label;
import com.application.ryft.issues.exception.AssigneeNotAProjectMemberException;
import com.application.ryft.issues.exception.IllegalStatusTransitionException;
import com.application.ryft.issues.exception.InsufficientProjectRoleException;
import com.application.ryft.issues.exception.InvalidComponentReferenceException;
import com.application.ryft.issues.exception.InvalidLabelReferenceException;
import com.application.ryft.issues.exception.InvalidParentLinkException;
import com.application.ryft.issues.exception.IssueNotFoundException;
import com.application.ryft.issues.exception.NotAnEpicException;
import com.application.ryft.issues.exception.NotAProjectMemberException;
import com.application.ryft.issues.repository.CommentRepository;
import com.application.ryft.issues.repository.ComponentRepository;
import com.application.ryft.issues.repository.IssueComponentRepository;
import com.application.ryft.issues.repository.IssueKeySequenceRepository;
import com.application.ryft.issues.repository.IssueLabelRepository;
import com.application.ryft.issues.repository.IssueRepository;
import com.application.ryft.issues.repository.LabelRepository;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.entity.StatusCategory;
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
import org.springframework.context.ApplicationEventPublisher;

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

    @Mock
    private IssueLabelRepository issueLabelRepository;

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private IssueComponentRepository issueComponentRepository;

    @Mock
    private ComponentRepository componentRepository;

    @Mock
    private IssueWorkflowAccess issueWorkflowAccess;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private IssueServiceImpl issueService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);

    private final UUID todoStatusId = UUID.randomUUID();
    private final UUID blockedStatusId = UUID.randomUUID();
    private final UUID inProgressStatusId = UUID.randomUUID();
    private final UUID doneStatusId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        IssueLabelingService issueLabelingService = new IssueLabelingService(issueLabelRepository, labelRepository,
                issueComponentRepository, componentRepository, issueWorkflowAccess);
        issueService = new IssueServiceImpl(issueRepository, issueKeySequenceRepository, projectAccess,
                commentRepository, issueLabelingService, issueWorkflowAccess, eventPublisher);
        org.mockito.Mockito.lenient().when(issueWorkflowAccess.requireScheme(any(), any())).thenReturn(defaultScheme());
        org.mockito.Mockito.lenient().when(projectAccess.getRole(callerId, "TRK")).thenReturn(ProjectRole.OWNER);
    }

    private WorkflowSchemeResponse defaultScheme() {
        return new WorkflowSchemeResponse(UUID.randomUUID(), projectId, "Default Workflow", List.of(
                new WorkflowStatusResponse(todoStatusId, "To Do", StatusCategory.TODO, 0),
                new WorkflowStatusResponse(blockedStatusId, "Blocked", StatusCategory.BLOCKED, 1),
                new WorkflowStatusResponse(inProgressStatusId, "In Progress", StatusCategory.IN_PROGRESS, 2),
                new WorkflowStatusResponse(doneStatusId, "Done", StatusCategory.DONE, 3)), List.of());
    }

    @Test
    void createGeneratesKeyAndDefaultsPriorityAndStatus() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.BUG, "Fix login", null, null, null, null, null, null, null));

        assertThat(result.key()).isEqualTo("TRK-1");
        assertThat(result.statusId()).isEqualTo(todoStatusId);
        assertThat(result.statusCategory()).isEqualTo(StatusCategory.TODO);
        assertThat(result.priority()).isEqualTo(IssuePriority.MEDIUM);
        assertThat(result.reporterId()).isEqualTo(callerId);
        assertThat(result.storyPoints()).isNull();
    }

    @Test
    void createSetsStoryPointsWhenProvided() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.BUG, "Fix login", null, null, null, 5, null, null, null));

        assertThat(result.storyPoints()).isEqualTo(5);
    }

    @Test
    void createUsesSecondSequenceNumberWhenSequenceExists() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        IssueKeySequence existing = new IssueKeySequence(projectId);
        existing.incrementAndGet();
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.of(existing));
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Second issue", null, IssuePriority.HIGH, null, null, null, null, null));

        assertThat(result.key()).isEqualTo("TRK-2");
        assertThat(result.priority()).isEqualTo(IssuePriority.HIGH);
    }

    @Test
    void createPropagatesProjectNotFound() {
        when(projectAccess.requireMembership(callerId, "TRK"))
                .thenThrow(new com.application.ryft.issues.exception.ProjectNotFoundException("TRK"));

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null)))
                .isInstanceOf(com.application.ryft.issues.exception.ProjectNotFoundException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createPropagatesNotAProjectMember() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenThrow(new NotAProjectMemberException());

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null)))
                .isInstanceOf(NotAProjectMemberException.class);
    }

    @Test
    void createRejectsViewer() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.getRole(callerId, "TRK")).thenReturn(ProjectRole.VIEWER);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null)))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(issueRepository, never()).save(any());
    }

    /** Loosened from Owner/Admin-only: any role except Viewer may create an issue. */
    @Test
    void createAsPlainMemberSucceeds() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.getRole(callerId, "TRK")).thenReturn(ProjectRole.MEMBER);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null, null, null, null, null));

        assertThat(result.key()).isEqualTo("TRK-1");
    }

    @Test
    void createRejectsAssigneeNotAProjectMember() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        UUID outsiderId = UUID.randomUUID();
        when(projectAccess.isMember(callerId, "TRK", outsiderId)).thenReturn(false);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, outsiderId, null, null, null, null)))
                .isInstanceOf(AssigneeNotAProjectMemberException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createAcceptsAssigneeThatIsAProjectMember() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        UUID assigneeId = UUID.randomUUID();
        when(projectAccess.isMember(callerId, "TRK", assigneeId)).thenReturn(true);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, assigneeId, null, null, null, null));

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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        IssueResponse result = issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest("New title", null, IssuePriority.HIGH, null, null, null, null, null));

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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        IssueResponse result = issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest(null, null, null, null, 8, null, null, null));

        assertThat(result.storyPoints()).isEqualTo(8);
    }

    @Test
    void updateWithNullStoryPointsLeavesExistingValueUnchanged() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Original", "orig desc", IssuePriority.LOW, null,
                callerId, 1000.0);
        issue.setStoryPoints(3);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        IssueResponse result = issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest("New title", null, null, null, null, null, null, null));

        assertThat(result.storyPoints()).isEqualTo(3);
    }

    @Test
    void updateRejectsUninvolvedMember() {
        UUID reporterId = UUID.randomUUID();
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                reporterId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.getRole(callerId, "TRK")).thenReturn(ProjectRole.MEMBER);

        assertThatThrownBy(() -> issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest("New title", null, null, null, null, null, null, null)))
                .isInstanceOf(InsufficientProjectRoleException.class);
    }

    @Test
    void updateSucceedsForMemberWhoIsTheAssignee() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, callerId,
                UUID.randomUUID(), 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.getRole(callerId, "TRK")).thenReturn(ProjectRole.MEMBER);

        IssueResponse result = issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest("New title", null, null, null, null, null, null, null));

        assertThat(result.title()).isEqualTo("New title");
    }

    @Test
    void updateSucceedsForMemberWhoIsTheReporter() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.getRole(callerId, "TRK")).thenReturn(ProjectRole.MEMBER);

        IssueResponse result = issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest("New title", null, null, null, null, null, null, null));

        assertThat(result.title()).isEqualTo("New title");
    }

    @Test
    void updateRejectsViewerEvenWhenInvolved() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.getRole(callerId, "TRK")).thenReturn(ProjectRole.VIEWER);

        assertThatThrownBy(() -> issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest("New title", null, null, null, null, null, null, null)))
                .isInstanceOf(InsufficientProjectRoleException.class);
    }

    @Test
    void updateRejectsAssigneeNotAProjectMember() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        UUID outsiderId = UUID.randomUUID();
        when(projectAccess.isMember(callerId, "TRK", outsiderId)).thenReturn(false);

        assertThatThrownBy(() -> issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest(null, null, null, outsiderId, null, null, null, null)))
                .isInstanceOf(AssigneeNotAProjectMemberException.class);
    }

    @Test
    void changeStatusToDoneSetsResolvedAt() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        issue.setWorkflowStatusId(todoStatusId);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(issueWorkflowAccess.isTransitionLegal(callerId, "TRK", todoStatusId, doneStatusId)).thenReturn(true);

        IssueResponse result = issueService.changeStatus(callerId, "TRK-1",
                new ChangeIssueStatusRequest(doneStatusId));

        assertThat(result.statusId()).isEqualTo(doneStatusId);
        assertThat(result.statusCategory()).isEqualTo(StatusCategory.DONE);
        assertThat(result.resolvedAt()).isNotNull();
        // Unlike create/update/delete, dragging a card is open to any project member — no Owner/Admin check.
        verify(projectAccess, never()).isOwnerOrAdmin(any(), any());
    }

    @Test
    void changeStatusAwayFromDoneClearsResolvedAt() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        issue.setWorkflowStatusId(doneStatusId);
        issue.setResolvedAt(Instant.now());
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(issueWorkflowAccess.isTransitionLegal(callerId, "TRK", doneStatusId, inProgressStatusId)).thenReturn(true);

        IssueResponse result = issueService.changeStatus(callerId, "TRK-1",
                new ChangeIssueStatusRequest(inProgressStatusId));

        assertThat(result.resolvedAt()).isNull();
    }

    @Test
    void changeStatusRequiresCallerToBeAProjectMember() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenThrow(new NotAProjectMemberException());

        assertThatThrownBy(() -> issueService.changeStatus(callerId, "TRK-1",
                new ChangeIssueStatusRequest(doneStatusId)))
                .isInstanceOf(NotAProjectMemberException.class);
    }

    @Test
    void changeStatusRejectsViewer() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.getRole(callerId, "TRK")).thenReturn(ProjectRole.VIEWER);

        assertThatThrownBy(() -> issueService.changeStatus(callerId, "TRK-1",
                new ChangeIssueStatusRequest(doneStatusId)))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(issueWorkflowAccess, never()).isTransitionLegal(any(), any(), any(), any());
    }

    @Test
    void changeStatusRejectsAnIllegalTransition() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        issue.setWorkflowStatusId(todoStatusId);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(issueWorkflowAccess.isTransitionLegal(callerId, "TRK", todoStatusId, doneStatusId)).thenReturn(false);

        assertThatThrownBy(() -> issueService.changeStatus(callerId, "TRK-1",
                new ChangeIssueStatusRequest(doneStatusId)))
                .isInstanceOf(IllegalStatusTransitionException.class);
        assertThat(issue.getWorkflowStatusId()).isEqualTo(todoStatusId);
    }

    @Test
    void changeStatusBackfillsLegacyRowsMissingWorkflowStatusIdBeforeCheckingLegality() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        // A pre-Phase-4 row: workflowStatusId is null, only the legacy TODO enum value is set.
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(issueWorkflowAccess.isTransitionLegal(callerId, "TRK", todoStatusId, doneStatusId)).thenReturn(true);

        IssueResponse result = issueService.changeStatus(callerId, "TRK-1", new ChangeIssueStatusRequest(doneStatusId));

        assertThat(result.statusId()).isEqualTo(doneStatusId);
    }

    @Test
    void deleteRemovesTheIssue() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> issueService.delete(callerId, "TRK-1"))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(issueRepository, never()).delete(any());
        verify(commentRepository, never()).deleteAllByIssueId(any());
    }

    /** Delete stays Owner/Admin-only even for a Member who is the issue's assignee/reporter — unlike update. */
    @Test
    void deleteRejectsInvolvedMember() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, callerId,
                UUID.randomUUID(), 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> issueService.delete(callerId, "TRK-1"))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(issueRepository, never()).delete(any());
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
        issue.setWorkflowStatusId(doneStatusId);
        when(issueRepository.findAllByProjectIdAndSprintIdAndTypeNotOrderByCreatedAtAsc(projectId, sprintId,
                IssueType.SUBTASK)).thenReturn(List.of(issue));

        List<IssueResponse> result = issueService.listForSprint(callerId, "TRK", sprintId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).statusId()).isEqualTo(doneStatusId);
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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.findFirstByProjectIdOrderByBacklogRankDesc(projectId)).thenReturn(Optional.empty());
        ArgumentCaptor<Issue> captor = ArgumentCaptor.forClass(Issue.class);
        when(issueRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        issueService.create(callerId, "TRK", new CreateIssueRequest(IssueType.BUG, "First", null, null, null, null, null, null, null));

        assertThat(captor.getValue().getBacklogRank()).isEqualTo(1000.0);
    }

    @Test
    void createAssignsSecondBacklogRankAfterTheExistingHighestRank() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        Issue existing = new Issue(projectId, "TRK-1", IssueType.BUG, "First", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findFirstByProjectIdOrderByBacklogRankDesc(projectId)).thenReturn(Optional.of(existing));
        ArgumentCaptor<Issue> captor = ArgumentCaptor.forClass(Issue.class);
        when(issueRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        issueService.create(callerId, "TRK", new CreateIssueRequest(IssueType.BUG, "Second", null, null, null, null, null, null, null));

        assertThat(captor.getValue().getBacklogRank()).isEqualTo(2000.0);
    }

    @Test
    void moveToSprintAssignsSprint() {
        UUID sprintId = UUID.randomUUID();
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        IssueResponse result = issueService.moveToSprint(callerId, "TRK-1", null);

        assertThat(result.sprintId()).isNull();
    }

    @Test
    void moveToSprintRejectsCallerWhoIsNotOwnerOrAdmin() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        issueService.reorderBacklog(callerId, "TRK-2", null, "TRK-1");

        assertThat(issue.getBacklogRank()).isEqualTo(0.0);
    }

    @Test
    void reorderBacklogWithNeitherNeighborLeavesRankUnchanged() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        issueService.reorderBacklog(callerId, "TRK-1", null, null);

        assertThat(issue.getBacklogRank()).isEqualTo(1000.0);
    }

    @Test
    void reorderBacklogRejectsCallerWhoIsNotOwnerOrAdmin() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(epicId, projectId)).thenReturn(Optional.of(epic));
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.STORY, "Story", null, null, null, null, epicId, null, null));

        assertThat(result.parentId()).isEqualTo(epicId);
    }

    @Test
    void createWithParentPointingToNonEpicThrows() {
        UUID parentId = UUID.randomUUID();
        Issue nonEpicParent = new Issue(projectId, "TRK-1", IssueType.TASK, "Not an epic", null,
                IssuePriority.MEDIUM, null, callerId, 1000.0);
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.of(nonEpicParent));

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.STORY, "Story", null, null, null, null, parentId, null, null)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createWithParentInDifferentProjectThrows() {
        UUID parentId = UUID.randomUUID();
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.empty());
        when(issueRepository.existsById(parentId)).thenReturn(true);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.STORY, "Story", null, null, null, null, parentId, null, null)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createWithUnknownParentThrows() {
        UUID parentId = UUID.randomUUID();
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.empty());
        when(issueRepository.existsById(parentId)).thenReturn(false);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.STORY, "Story", null, null, null, null, parentId, null, null)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createEpicWithNonNullParentThrows() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.EPIC, "Epic", null, null, null, null, UUID.randomUUID(), null, null)))
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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(epicId, projectId)).thenReturn(Optional.of(epic));

        IssueResponse result = issueService.update(callerId, "TRK-2",
                new UpdateIssueRequest(null, null, null, null, null, epicId, null, null));

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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.of(nonEpicParent));

        assertThatThrownBy(() -> issueService.update(callerId, "TRK-2",
                new UpdateIssueRequest(null, null, null, null, null, parentId, null, null)))
                .isInstanceOf(InvalidParentLinkException.class);
        assertThat(issue.getParentIssueId()).isNull();
    }

    @Test
    void updateOnAnEpicWithParentThrows() {
        Issue epic = new Issue(projectId, "TRK-1", IssueType.EPIC, "Epic", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(epic));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest(null, null, null, null, null, UUID.randomUUID(), null, null)))
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
        unfinished.setWorkflowStatusId(todoStatusId);
        Issue done = new Issue(projectId, "TRK-2", IssueType.BUG, "Done", null, IssuePriority.MEDIUM, null, callerId,
                2000.0);
        done.setSprintId(sprintId);
        done.setWorkflowStatusId(doneStatusId);
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(issueRepository.findAllByProjectIdAndSprintId(projectId, sprintId)).thenReturn(List.of(unfinished, done));

        issueService.moveUnfinishedIssuesToBacklog(callerId, "TRK", sprintId);

        assertThat(unfinished.getSprintId()).isNull();
        assertThat(done.getSprintId()).isEqualTo(sprintId);
        verify(projectAccess, never()).isOwnerOrAdmin(any(), any());
    }

    /**
     * "Done" is every status in the DONE category, not one fixed id — a second DONE-category status
     * (e.g. an admin-added "Shipped" alongside "Done") must be treated as finished too.
     */
    @Test
    void moveUnfinishedIssuesToBacklogTreatsEveryDoneCategoryStatusAsFinished() {
        UUID shippedStatusId = UUID.randomUUID();
        WorkflowSchemeResponse schemeWithTwoDoneStatuses = new WorkflowSchemeResponse(UUID.randomUUID(), projectId,
                "Default Workflow",
                List.of(new WorkflowStatusResponse(todoStatusId, "To Do", StatusCategory.TODO, 0),
                        new WorkflowStatusResponse(doneStatusId, "Done", StatusCategory.DONE, 1),
                        new WorkflowStatusResponse(shippedStatusId, "Shipped", StatusCategory.DONE, 2)),
                List.of());
        when(issueWorkflowAccess.requireScheme(callerId, "TRK")).thenReturn(schemeWithTwoDoneStatuses);
        UUID sprintId = UUID.randomUUID();
        Issue shipped = new Issue(projectId, "TRK-1", IssueType.BUG, "Shipped", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        shipped.setSprintId(sprintId);
        shipped.setWorkflowStatusId(shippedStatusId);
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(issueRepository.findAllByProjectIdAndSprintId(projectId, sprintId)).thenReturn(List.of(shipped));

        issueService.moveUnfinishedIssuesToBacklog(callerId, "TRK", sprintId);

        assertThat(shipped.getSprintId()).isEqualTo(sprintId);
    }

    @Test
    void createSubtaskTypeWithoutParentIdThrows() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.SUBTASK, "Subtask", null, null, null, null, null, null, null)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createSubtaskTypeWithEpicParentThrows() {
        UUID parentId = UUID.randomUUID();
        Issue epicParent = new Issue(projectId, "TRK-1", IssueType.EPIC, "Epic", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.of(epicParent));

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.SUBTASK, "Subtask", null, null, null, null, parentId, null, null)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createSubtaskTypeWithParentInDifferentProjectThrows() {
        UUID parentId = UUID.randomUUID();
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.empty());
        when(issueRepository.existsById(parentId)).thenReturn(true);

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.SUBTASK, "Subtask", null, null, null, null, parentId, null, null)))
                .isInstanceOf(InvalidParentLinkException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createSubtaskTypeWithStoryParentSucceeds() {
        UUID parentId = UUID.randomUUID();
        Issue storyParent = new Issue(projectId, "TRK-1", IssueType.STORY, "Story", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(parentId, projectId)).thenReturn(Optional.of(storyParent));
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.SUBTASK, "Subtask", null, null, null, null, parentId, null, null));

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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(epicId, projectId)).thenReturn(Optional.of(epic));

        assertThatThrownBy(() -> issueService.update(callerId, "TRK-2",
                new UpdateIssueRequest(null, null, null, null, null, epicId, null, null)))
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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueRepository.findByIdAndProjectId(newParentId, projectId)).thenReturn(Optional.of(newParent));

        IssueResponse result = issueService.update(callerId, "TRK-2",
                new UpdateIssueRequest(null, null, null, null, null, newParentId, null, null));

        assertThat(result.parentId()).isEqualTo(newParentId);
    }

    @Test
    void createSubtaskRejectsEpicParent() {
        Issue parent = new Issue(projectId, "TRK-1", IssueType.EPIC, "Epic", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(parent));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
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
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
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

    @Test
    void getEpicProgressWithNoLinkedIssuesReturnsZeroPercentWithoutDivideByZero() {
        Issue epic = new Issue(projectId, "TRK-1", IssueType.EPIC, "Epic", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        org.springframework.test.util.ReflectionTestUtils.setField(epic, "id", UUID.randomUUID());
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(epic));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(issueRepository.countByProjectIdAndParentIssueId(projectId, epic.getId())).thenReturn(0L);
        when(issueWorkflowAccess.getStatusIdsInCategory(callerId, "TRK", StatusCategory.DONE))
                .thenReturn(List.of(doneStatusId));
        when(issueRepository.countByProjectIdAndParentIssueIdAndWorkflowStatusIdIn(projectId, epic.getId(),
                List.of(doneStatusId))).thenReturn(0L);

        EpicProgressResponse result = issueService.getEpicProgress(callerId, "TRK-1");

        assertThat(result).isEqualTo(new EpicProgressResponse(0, 0, 0.0));
    }

    @Test
    void getEpicProgressWithSomeDoneAndSomeNotComputesPercent() {
        Issue epic = new Issue(projectId, "TRK-1", IssueType.EPIC, "Epic", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        org.springframework.test.util.ReflectionTestUtils.setField(epic, "id", UUID.randomUUID());
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(epic));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(issueRepository.countByProjectIdAndParentIssueId(projectId, epic.getId())).thenReturn(4L);
        when(issueWorkflowAccess.getStatusIdsInCategory(callerId, "TRK", StatusCategory.DONE))
                .thenReturn(List.of(doneStatusId));
        when(issueRepository.countByProjectIdAndParentIssueIdAndWorkflowStatusIdIn(projectId, epic.getId(),
                List.of(doneStatusId))).thenReturn(1L);

        EpicProgressResponse result = issueService.getEpicProgress(callerId, "TRK-1");

        assertThat(result.totalCount()).isEqualTo(4);
        assertThat(result.doneCount()).isEqualTo(1);
        assertThat(result.percentDone()).isEqualTo(25.0);
    }

    @Test
    void getEpicProgressWithAllLinkedIssuesDoneReturnsFullPercent() {
        Issue epic = new Issue(projectId, "TRK-1", IssueType.EPIC, "Epic", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        org.springframework.test.util.ReflectionTestUtils.setField(epic, "id", UUID.randomUUID());
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(epic));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(issueRepository.countByProjectIdAndParentIssueId(projectId, epic.getId())).thenReturn(3L);
        when(issueWorkflowAccess.getStatusIdsInCategory(callerId, "TRK", StatusCategory.DONE))
                .thenReturn(List.of(doneStatusId));
        when(issueRepository.countByProjectIdAndParentIssueIdAndWorkflowStatusIdIn(projectId, epic.getId(),
                List.of(doneStatusId))).thenReturn(3L);

        EpicProgressResponse result = issueService.getEpicProgress(callerId, "TRK-1");

        assertThat(result.percentDone()).isEqualTo(100.0);
    }

    @Test
    void getEpicProgressOnNonEpicThrows() {
        Issue story = new Issue(projectId, "TRK-2", IssueType.STORY, "Story", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-2")).thenReturn(Optional.of(story));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);

        assertThatThrownBy(() -> issueService.getEpicProgress(callerId, "TRK-2"))
                .isInstanceOf(NotAnEpicException.class);
        verify(issueRepository, never()).countByProjectIdAndParentIssueId(any(), any());
    }

    @Test
    void getEpicProgressRequiresEpicToExist() {
        when(issueRepository.findByKey("TRK-9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> issueService.getEpicProgress(callerId, "trk-9"))
                .isInstanceOf(IssueNotFoundException.class);
    }

    private Label labelWithId(String name) {
        Label label = new Label(projectId, name, "#FF0000");
        org.springframework.test.util.ReflectionTestUtils.setField(label, "id", UUID.randomUUID());
        return label;
    }

    private Component componentWithId(String name) {
        Component component = new Component(projectId, name);
        org.springframework.test.util.ReflectionTestUtils.setField(component, "id", UUID.randomUUID());
        return component;
    }

    @Test
    void createAttachesValidLabelsAndComponents() {
        Label label = labelWithId("Bug");
        Component component = componentWithId("Backend");
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(labelRepository.findAllByProjectIdAndIdIn(projectId, List.of(label.getId()))).thenReturn(List.of(label));
        when(componentRepository.findAllByProjectIdAndIdIn(projectId, List.of(component.getId())))
                .thenReturn(List.of(component));
        when(issueLabelRepository.findAllByIssueIdIn(any())).thenReturn(List.of());
        when(issueComponentRepository.findAllByIssueIdIn(any())).thenReturn(List.of());

        issueService.create(callerId, "TRK", new CreateIssueRequest(IssueType.BUG, "Fix login", null, null, null,
                null, null, List.of(label.getId()), List.of(component.getId())));

        verify(issueLabelRepository).saveAll(any());
        verify(issueComponentRepository).saveAll(any());
    }

    @Test
    void createRejectsLabelFromAnotherProject() {
        UUID foreignLabelId = UUID.randomUUID();
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(labelRepository.findAllByProjectIdAndIdIn(projectId, List.of(foreignLabelId))).thenReturn(List.of());

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.BUG, "Fix login", null, null, null, null, null,
                        List.of(foreignLabelId), null)))
                .isInstanceOf(InvalidLabelReferenceException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createRejectsComponentFromAnotherProject() {
        UUID foreignComponentId = UUID.randomUUID();
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(componentRepository.findAllByProjectIdAndIdIn(projectId, List.of(foreignComponentId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.BUG, "Fix login", null, null, null, null, null, null,
                        List.of(foreignComponentId))))
                .isInstanceOf(InvalidComponentReferenceException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void updateWithNullLabelIdsLeavesExistingLabelsUnchanged() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueLabelRepository.findAllByIssueIdIn(any())).thenReturn(List.of());
        when(issueComponentRepository.findAllByIssueIdIn(any())).thenReturn(List.of());

        issueService.update(callerId, "TRK-1", new UpdateIssueRequest(null, null, null, null, null, null, null, null));

        verify(issueLabelRepository, never()).deleteAllByIssueId(any());
        verify(labelRepository, never()).findAllByProjectIdAndIdIn(any(), any());
    }

    @Test
    void updateWithEmptyLabelIdsClearsExistingLabels() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueLabelRepository.findAllByIssueIdIn(any())).thenReturn(List.of());
        when(issueComponentRepository.findAllByIssueIdIn(any())).thenReturn(List.of());

        issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest(null, null, null, null, null, null, List.of(), null));

        verify(issueLabelRepository).deleteAllByIssueId(issue.getId());
        verify(issueLabelRepository).saveAll(List.of());
    }

    @Test
    void listForProjectByLabelDelegatesToLabelScopedQuery() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        UUID labelId = UUID.randomUUID();
        Issue issue = new Issue(projectId, "TRK-1", IssueType.STORY, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findAllByProjectIdAndLabelIdAndTypeNotOrderByCreatedAtAsc(projectId, labelId,
                IssueType.SUBTASK)).thenReturn(List.of(issue));
        when(issueLabelRepository.findAllByIssueIdIn(any())).thenReturn(List.of());
        when(issueComponentRepository.findAllByIssueIdIn(any())).thenReturn(List.of());

        List<IssueResponse> result = issueService.listForProjectByLabel(callerId, "TRK", labelId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).key()).isEqualTo("TRK-1");
    }

    @Test
    void listForProjectByComponentDelegatesToComponentScopedQuery() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        UUID componentId = UUID.randomUUID();
        Issue issue = new Issue(projectId, "TRK-1", IssueType.STORY, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findAllByProjectIdAndComponentIdAndTypeNotOrderByCreatedAtAsc(projectId, componentId,
                IssueType.SUBTASK)).thenReturn(List.of(issue));
        when(issueLabelRepository.findAllByIssueIdIn(any())).thenReturn(List.of());
        when(issueComponentRepository.findAllByIssueIdIn(any())).thenReturn(List.of());

        List<IssueResponse> result = issueService.listForProjectByComponent(callerId, "TRK", componentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).key()).isEqualTo("TRK-1");
    }

    @Test
    void createPublishesIssueCreatedEvent() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.BUG, "Fix login", null, null, null, null, null, null, null));

        ArgumentCaptor<IssueCreatedEvent> captor = ArgumentCaptor.forClass(IssueCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        IssueCreatedEvent event = captor.getValue();
        assertThat(event.issueKey()).isEqualTo("TRK-1");
        assertThat(event.projectId()).isEqualTo(projectId);
        assertThat(event.projectKey()).isEqualTo("TRK");
        assertThat(event.actorId()).isEqualTo(callerId);
        assertThat(event.reporterId()).isEqualTo(callerId);
        assertThat(event.title()).isEqualTo("Fix login");
        assertThat(event.issueType()).isEqualTo("BUG");
    }

    @Test
    void changeStatusPublishesStatusChangedEventWithResolvedStatusNames() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        issue.setWorkflowStatusId(todoStatusId);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(issueWorkflowAccess.isTransitionLegal(callerId, "TRK", todoStatusId, doneStatusId)).thenReturn(true);

        issueService.changeStatus(callerId, "TRK-1", new ChangeIssueStatusRequest(doneStatusId));

        ArgumentCaptor<IssueStatusChangedEvent> captor = ArgumentCaptor.forClass(IssueStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        IssueStatusChangedEvent event = captor.getValue();
        assertThat(event.issueKey()).isEqualTo("TRK-1");
        assertThat(event.fromStatus()).isEqualTo("To Do");
        assertThat(event.toStatus()).isEqualTo("Done");
    }

    @Test
    void updatePublishesAssigneeChangedEventWhenAssigneeChanges() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        UUID newAssigneeId = UUID.randomUUID();
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(projectAccess.isMember(callerId, "TRK", newAssigneeId)).thenReturn(true);

        issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest(null, null, null, newAssigneeId, null, null, null, null));

        ArgumentCaptor<IssueAssigneeChangedEvent> captor = ArgumentCaptor.forClass(IssueAssigneeChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        IssueAssigneeChangedEvent event = captor.getValue();
        assertThat(event.previousAssigneeId()).isNull();
        assertThat(event.newAssigneeId()).isEqualTo(newAssigneeId);
    }

    @Test
    void updateDoesNotPublishAssigneeChangedEventWhenAssigneeIsUnchanged() {
        // caller is the assignee (and thus an "involved Member" allowed to edit), reassigning to themselves.
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, callerId,
                UUID.randomUUID(), 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.getRole(callerId, "TRK")).thenReturn(ProjectRole.MEMBER);
        when(projectAccess.isMember(callerId, "TRK", callerId)).thenReturn(true);

        issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest(null, null, null, callerId, null, null, null, null));

        verify(eventPublisher, never()).publishEvent(any(IssueAssigneeChangedEvent.class));
    }

    @Test
    void updateDoesNotPublishAnyEventWhenAssigneeFieldIsAbsent() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                callerId, 1000.0);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        lenient().when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);

        issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest("New title", null, null, null, null, null, null, null));

        verify(eventPublisher, never()).publishEvent(any(IssueAssigneeChangedEvent.class));
    }
}
