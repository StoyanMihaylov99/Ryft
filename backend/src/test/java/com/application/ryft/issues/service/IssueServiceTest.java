package com.application.ryft.issues.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.UpdateIssueRequest;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssueKeySequence;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.exception.AssigneeNotAProjectMemberException;
import com.application.ryft.issues.exception.IssueNotFoundException;
import com.application.ryft.issues.exception.NotAProjectMemberException;
import com.application.ryft.issues.exception.ProjectNotFoundException;
import com.application.ryft.issues.repository.IssueKeySequenceRepository;
import com.application.ryft.issues.repository.IssueRepository;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.dto.ProjectMemberResponse;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.projects.service.ProjectService;
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
    private ProjectService projectService;

    private IssueServiceImpl issueService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);

    @BeforeEach
    void setUp() {
        issueService = new IssueServiceImpl(issueRepository, issueKeySequenceRepository, projectService);
    }

    @Test
    void createGeneratesKeyAndDefaultsPriorityAndStatus() {
        when(projectService.get(callerId, "TRK")).thenReturn(project);
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.BUG, "Fix login", null, null, null));

        assertThat(result.key()).isEqualTo("TRK-1");
        assertThat(result.status()).isEqualTo(IssueStatus.TODO);
        assertThat(result.priority()).isEqualTo(IssuePriority.MEDIUM);
        assertThat(result.reporterId()).isEqualTo(callerId);
    }

    @Test
    void createUsesSecondSequenceNumberWhenSequenceExists() {
        when(projectService.get(callerId, "TRK")).thenReturn(project);
        IssueKeySequence existing = new IssueKeySequence(projectId);
        existing.incrementAndGet();
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.of(existing));
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Second issue", null, IssuePriority.HIGH, null));

        assertThat(result.key()).isEqualTo("TRK-2");
        assertThat(result.priority()).isEqualTo(IssuePriority.HIGH);
    }

    @Test
    void createTranslatesProjectNotFound() {
        when(projectService.get(callerId, "TRK"))
                .thenThrow(new com.application.ryft.projects.exception.ProjectNotFoundException("TRK"));

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null)))
                .isInstanceOf(ProjectNotFoundException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createTranslatesNotAProjectMember() {
        when(projectService.get(callerId, "TRK"))
                .thenThrow(new com.application.ryft.projects.exception.NotAProjectMemberException());

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, null)))
                .isInstanceOf(NotAProjectMemberException.class);
    }

    @Test
    void createRejectsAssigneeNotAProjectMember() {
        when(projectService.get(callerId, "TRK")).thenReturn(project);
        UUID outsiderId = UUID.randomUUID();
        when(projectService.listMembers(callerId, "TRK")).thenReturn(List.of());

        assertThatThrownBy(() -> issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, outsiderId)))
                .isInstanceOf(AssigneeNotAProjectMemberException.class);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void createAcceptsAssigneeThatIsAProjectMember() {
        when(projectService.get(callerId, "TRK")).thenReturn(project);
        UUID assigneeId = UUID.randomUUID();
        when(projectService.listMembers(callerId, "TRK")).thenReturn(
                List.of(new ProjectMemberResponse(assigneeId, "a@example.com", "A", null, ProjectRole.MEMBER, Instant.now())));
        when(issueKeySequenceRepository.findForUpdate(projectId)).thenReturn(Optional.empty());
        when(issueKeySequenceRepository.save(any(IssueKeySequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse result = issueService.create(callerId, "TRK",
                new CreateIssueRequest(IssueType.TASK, "Title", null, null, assigneeId));

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
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectService.get(callerId, "TRK"))
                .thenThrow(new com.application.ryft.projects.exception.NotAProjectMemberException());

        assertThatThrownBy(() -> issueService.get(callerId, "TRK-1"))
                .isInstanceOf(NotAProjectMemberException.class);
    }

    @Test
    void updateAppliesProvidedFieldsOnly() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Original", "orig desc", IssuePriority.LOW, null,
                callerId);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectService.get(callerId, "TRK")).thenReturn(project);

        IssueResponse result = issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest("New title", null, IssuePriority.HIGH, null, null));

        assertThat(result.title()).isEqualTo("New title");
        assertThat(result.description()).isEqualTo("orig desc");
        assertThat(result.priority()).isEqualTo(IssuePriority.HIGH);
    }

    @Test
    void updateToDoneSetsResolvedAt() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectService.get(callerId, "TRK")).thenReturn(project);

        IssueResponse result = issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest(null, null, null, null, IssueStatus.DONE));

        assertThat(result.status()).isEqualTo(IssueStatus.DONE);
        assertThat(result.resolvedAt()).isNotNull();
    }

    @Test
    void updateAwayFromDoneClearsResolvedAt() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId);
        issue.setStatus(IssueStatus.DONE);
        issue.setResolvedAt(Instant.now());
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectService.get(callerId, "TRK")).thenReturn(project);

        IssueResponse result = issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest(null, null, null, null, IssueStatus.IN_PROGRESS));

        assertThat(result.resolvedAt()).isNull();
    }

    @Test
    void updateRejectsAssigneeNotAProjectMember() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectService.get(callerId, "TRK")).thenReturn(project);
        UUID outsiderId = UUID.randomUUID();
        when(projectService.listMembers(callerId, "TRK")).thenReturn(List.of());

        assertThatThrownBy(() -> issueService.update(callerId, "TRK-1",
                new UpdateIssueRequest(null, null, null, outsiderId, null)))
                .isInstanceOf(AssigneeNotAProjectMemberException.class);
    }

    @Test
    void deleteRemovesTheIssue() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null, callerId);
        when(issueRepository.findByKey("TRK-1")).thenReturn(Optional.of(issue));
        when(projectService.get(callerId, "TRK")).thenReturn(project);

        issueService.delete(callerId, "TRK-1");

        ArgumentCaptor<Issue> captor = ArgumentCaptor.forClass(Issue.class);
        verify(issueRepository).delete(captor.capture());
        assertThat(captor.getValue().getKey()).isEqualTo("TRK-1");
    }

    @Test
    void listForProjectReturnsProjectsIssues() {
        when(projectService.get(callerId, "TRK")).thenReturn(project);
        Issue issue = new Issue(projectId, "TRK-1", IssueType.STORY, "Title", null, IssuePriority.MEDIUM, null, callerId);
        when(issueRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(List.of(issue));

        List<IssueResponse> result = issueService.listForProject(callerId, "TRK");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).key()).isEqualTo("TRK-1");
    }
}
