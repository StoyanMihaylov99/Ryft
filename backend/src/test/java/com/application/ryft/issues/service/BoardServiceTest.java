package com.application.ryft.issues.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.application.ryft.issues.dto.BoardResponse;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.exception.NotAProjectMemberException;
import com.application.ryft.issues.repository.ComponentRepository;
import com.application.ryft.issues.repository.IssueComponentRepository;
import com.application.ryft.issues.repository.IssueLabelRepository;
import com.application.ryft.issues.repository.IssueRepository;
import com.application.ryft.issues.repository.LabelRepository;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import com.application.ryft.workflow.service.WorkflowService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private IssueProjectAccess projectAccess;

    @Mock
    private WorkflowService workflowService;

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

    private BoardServiceImpl boardService;

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
        boardService = new BoardServiceImpl(issueRepository, projectAccess, workflowService, issueLabelingService);
        lenient().when(issueLabelRepository.findAllByIssueIdIn(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        lenient().when(issueComponentRepository.findAllByIssueIdIn(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        lenient().when(projectAccess.getRole(callerId, "TRK")).thenReturn(ProjectRole.OWNER);
    }

    private WorkflowSchemeResponse defaultScheme() {
        return new WorkflowSchemeResponse(UUID.randomUUID(), projectId, "Default Workflow", List.of(
                new WorkflowStatusResponse(todoStatusId, "To Do", StatusCategory.TODO, 0),
                new WorkflowStatusResponse(blockedStatusId, "Blocked", StatusCategory.BLOCKED, 1),
                new WorkflowStatusResponse(inProgressStatusId, "In Progress", StatusCategory.IN_PROGRESS, 2),
                new WorkflowStatusResponse(doneStatusId, "Done", StatusCategory.DONE, 3)), List.of());
    }

    private Issue issueWithStatus(String key, IssueType type, double rank, UUID statusId) {
        Issue issue = new Issue(projectId, key, type, key + " title", null, IssuePriority.MEDIUM, null, callerId,
                rank);
        issue.setWorkflowStatusId(statusId);
        return issue;
    }

    @Test
    void groupsIssuesIntoColumnsByStatusInSortOrder() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(workflowService.getSchemeForProject(callerId, "TRK")).thenReturn(defaultScheme());
        Issue todoIssue = issueWithStatus("TRK-1", IssueType.TASK, 1000.0, todoStatusId);
        Issue blockedIssue = issueWithStatus("TRK-3", IssueType.TASK, 2000.0, blockedStatusId);
        Issue doneIssue = issueWithStatus("TRK-2", IssueType.BUG, 3000.0, doneStatusId);
        when(issueRepository.findAllByProjectIdAndTypeNotOrderByCreatedAtAsc(projectId, IssueType.SUBTASK))
                .thenReturn(List.of(todoIssue, blockedIssue, doneIssue));

        BoardResponse result = boardService.getBoard(callerId, "TRK");

        assertThat(result.projectKey()).isEqualTo("TRK");
        assertThat(result.columns()).hasSize(4);
        assertThat(result.columns().get(0).category()).isEqualTo(StatusCategory.TODO);
        assertThat(result.columns().get(0).issues()).extracting("key").containsExactly("TRK-1");
        assertThat(result.columns().get(1).category()).isEqualTo(StatusCategory.BLOCKED);
        assertThat(result.columns().get(1).issues()).extracting("key").containsExactly("TRK-3");
        assertThat(result.columns().get(2).category()).isEqualTo(StatusCategory.IN_PROGRESS);
        assertThat(result.columns().get(2).issues()).isEmpty();
        assertThat(result.columns().get(3).category()).isEqualTo(StatusCategory.DONE);
        assertThat(result.columns().get(3).issues()).extracting("key").containsExactly("TRK-2");
    }

    /**
     * Regression for the Phase-4 switch from category-name matching to direct status-id matching: two
     * different statuses sharing the same category must land in two distinct columns, not be merged.
     */
    @Test
    void twoStatusesSharingACategoryDoNotDoubleCountIssues() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        UUID inReviewStatusId = UUID.randomUUID();
        WorkflowSchemeResponse scheme = new WorkflowSchemeResponse(UUID.randomUUID(), projectId, "Default Workflow",
                List.of(new WorkflowStatusResponse(todoStatusId, "To Do", StatusCategory.TODO, 0),
                        new WorkflowStatusResponse(inProgressStatusId, "In Progress", StatusCategory.IN_PROGRESS, 1),
                        new WorkflowStatusResponse(inReviewStatusId, "In Review", StatusCategory.IN_PROGRESS, 2)),
                List.of());
        when(workflowService.getSchemeForProject(callerId, "TRK")).thenReturn(scheme);
        Issue inProgressIssue = issueWithStatus("TRK-1", IssueType.TASK, 1000.0, inProgressStatusId);
        Issue inReviewIssue = issueWithStatus("TRK-2", IssueType.TASK, 2000.0, inReviewStatusId);
        when(issueRepository.findAllByProjectIdAndTypeNotOrderByCreatedAtAsc(projectId, IssueType.SUBTASK))
                .thenReturn(List.of(inProgressIssue, inReviewIssue));

        BoardResponse result = boardService.getBoard(callerId, "TRK");

        assertThat(result.columns()).hasSize(3);
        assertThat(result.columns().get(1).name()).isEqualTo("In Progress");
        assertThat(result.columns().get(1).issues()).extracting("key").containsExactly("TRK-1");
        assertThat(result.columns().get(2).name()).isEqualTo("In Review");
        assertThat(result.columns().get(2).issues()).extracting("key").containsExactly("TRK-2");
    }

    @Test
    void requiresCallerToBeAProjectMember() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenThrow(new NotAProjectMemberException());

        assertThatThrownBy(() -> boardService.getBoard(callerId, "TRK"))
                .isInstanceOf(NotAProjectMemberException.class);
    }
}
