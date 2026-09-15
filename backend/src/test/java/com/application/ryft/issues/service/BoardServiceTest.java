package com.application.ryft.issues.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.application.ryft.issues.dto.BoardResponse;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.exception.NotAProjectMemberException;
import com.application.ryft.issues.repository.IssueRepository;
import com.application.ryft.projects.dto.ProjectResponse;
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

    private BoardServiceImpl boardService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);

    @BeforeEach
    void setUp() {
        boardService = new BoardServiceImpl(issueRepository, projectAccess, workflowService);
    }

    private WorkflowSchemeResponse defaultScheme() {
        return new WorkflowSchemeResponse(UUID.randomUUID(), projectId, "Default Workflow", List.of(
                new WorkflowStatusResponse(UUID.randomUUID(), "To Do", StatusCategory.TODO, 0),
                new WorkflowStatusResponse(UUID.randomUUID(), "In Progress", StatusCategory.IN_PROGRESS, 1),
                new WorkflowStatusResponse(UUID.randomUUID(), "Done", StatusCategory.DONE, 2)));
    }

    @Test
    void groupsIssuesIntoColumnsByStatusInSortOrder() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(workflowService.getSchemeForProject(callerId, "TRK")).thenReturn(defaultScheme());
        Issue todoIssue = new Issue(projectId, "TRK-1", IssueType.TASK, "Todo issue", null, IssuePriority.MEDIUM,
                null, callerId);
        Issue doneIssue = new Issue(projectId, "TRK-2", IssueType.BUG, "Done issue", null, IssuePriority.MEDIUM,
                null, callerId);
        doneIssue.setStatus(IssueStatus.DONE);
        when(issueRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(List.of(todoIssue, doneIssue));

        BoardResponse result = boardService.getBoard(callerId, "TRK");

        assertThat(result.projectKey()).isEqualTo("TRK");
        assertThat(result.columns()).hasSize(3);
        assertThat(result.columns().get(0).category()).isEqualTo(StatusCategory.TODO);
        assertThat(result.columns().get(0).issues()).extracting("key").containsExactly("TRK-1");
        assertThat(result.columns().get(1).category()).isEqualTo(StatusCategory.IN_PROGRESS);
        assertThat(result.columns().get(1).issues()).isEmpty();
        assertThat(result.columns().get(2).category()).isEqualTo(StatusCategory.DONE);
        assertThat(result.columns().get(2).issues()).extracting("key").containsExactly("TRK-2");
    }

    @Test
    void requiresCallerToBeAProjectMember() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenThrow(new NotAProjectMemberException());

        assertThatThrownBy(() -> boardService.getBoard(callerId, "TRK"))
                .isInstanceOf(NotAProjectMemberException.class);
    }
}
