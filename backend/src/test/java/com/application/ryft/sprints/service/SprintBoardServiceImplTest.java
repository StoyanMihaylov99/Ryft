package com.application.ryft.sprints.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.application.ryft.issues.dto.ComponentResponse;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.LabelResponse;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.service.IssueService;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.sprints.dto.SprintBoardResponse;
import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.entity.SprintState;
import com.application.ryft.sprints.exception.NoActiveSprintException;
import com.application.ryft.sprints.exception.NotAProjectMemberException;
import com.application.ryft.sprints.repository.SprintRepository;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import com.application.ryft.workflow.service.WorkflowService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SprintBoardServiceImplTest {

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private SprintsProjectAccess projectAccess;

    @Mock
    private WorkflowService workflowService;

    @Mock
    private IssueService issueService;

    private SprintBoardServiceImpl sprintBoardService;

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
        sprintBoardService = new SprintBoardServiceImpl(sprintRepository, projectAccess, workflowService,
                issueService);
    }

    private WorkflowSchemeResponse defaultScheme() {
        return new WorkflowSchemeResponse(UUID.randomUUID(), projectId, "Default Workflow", List.of(
                new WorkflowStatusResponse(todoStatusId, "To Do", StatusCategory.TODO, 0),
                new WorkflowStatusResponse(blockedStatusId, "Blocked", StatusCategory.BLOCKED, 1),
                new WorkflowStatusResponse(inProgressStatusId, "In Progress", StatusCategory.IN_PROGRESS, 2),
                new WorkflowStatusResponse(doneStatusId, "Done", StatusCategory.DONE, 3)), List.of());
    }

    private IssueResponse issue(String key, UUID statusId, String statusName, StatusCategory category) {
        return new IssueResponse(UUID.randomUUID(), projectId, key, IssueType.TASK, "Title", null, statusId,
                statusName, category, IssuePriority.MEDIUM, null, callerId, UUID.randomUUID(), null, null,
                List.<LabelResponse>of(), List.<ComponentResponse>of(), Instant.now(), Instant.now(), null, false);
    }

    @Test
    void groupsIssuesIntoColumnsByStatusInSortOrder() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, null, null);
        sprint.setState(SprintState.ACTIVE);
        when(sprintRepository.findByProjectIdAndState(projectId, SprintState.ACTIVE)).thenReturn(Optional.of(sprint));
        when(workflowService.getSchemeForProject(callerId, "TRK")).thenReturn(defaultScheme());
        IssueResponse todoIssue = issue("TRK-1", todoStatusId, "To Do", StatusCategory.TODO);
        IssueResponse blockedIssue = issue("TRK-3", blockedStatusId, "Blocked", StatusCategory.BLOCKED);
        IssueResponse doneIssue = issue("TRK-2", doneStatusId, "Done", StatusCategory.DONE);
        when(issueService.listForSprint(callerId, "TRK", sprint.getId()))
                .thenReturn(List.of(todoIssue, blockedIssue, doneIssue));

        SprintBoardResponse result = sprintBoardService.getBoard(callerId, "TRK");

        assertThat(result.projectKey()).isEqualTo("TRK");
        assertThat(result.sprintName()).isEqualTo("Sprint 1");
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
     * different statuses sharing the same category (e.g. a project that added a second "In Review"
     * status still categorized IN_PROGRESS) must land in two distinct columns, not be merged/double
     * counted into whichever IN_PROGRESS column happens to come first.
     */
    @Test
    void twoStatusesSharingACategoryDoNotDoubleCountIssues() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        Sprint sprint = new Sprint(projectId, "Sprint 1", null, null, null);
        sprint.setState(SprintState.ACTIVE);
        when(sprintRepository.findByProjectIdAndState(projectId, SprintState.ACTIVE)).thenReturn(Optional.of(sprint));
        UUID inReviewStatusId = UUID.randomUUID();
        WorkflowSchemeResponse scheme = new WorkflowSchemeResponse(UUID.randomUUID(), projectId, "Default Workflow",
                List.of(new WorkflowStatusResponse(todoStatusId, "To Do", StatusCategory.TODO, 0),
                        new WorkflowStatusResponse(inProgressStatusId, "In Progress", StatusCategory.IN_PROGRESS, 1),
                        new WorkflowStatusResponse(inReviewStatusId, "In Review", StatusCategory.IN_PROGRESS, 2)),
                List.of());
        when(workflowService.getSchemeForProject(callerId, "TRK")).thenReturn(scheme);
        IssueResponse inProgressIssue = issue("TRK-1", inProgressStatusId, "In Progress", StatusCategory.IN_PROGRESS);
        IssueResponse inReviewIssue = issue("TRK-2", inReviewStatusId, "In Review", StatusCategory.IN_PROGRESS);
        when(issueService.listForSprint(callerId, "TRK", sprint.getId()))
                .thenReturn(List.of(inProgressIssue, inReviewIssue));

        SprintBoardResponse result = sprintBoardService.getBoard(callerId, "TRK");

        assertThat(result.columns()).hasSize(3);
        assertThat(result.columns().get(1).name()).isEqualTo("In Progress");
        assertThat(result.columns().get(1).issues()).extracting("key").containsExactly("TRK-1");
        assertThat(result.columns().get(2).name()).isEqualTo("In Review");
        assertThat(result.columns().get(2).issues()).extracting("key").containsExactly("TRK-2");
    }

    @Test
    void throwsWhenNoActiveSprintExists() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(sprintRepository.findByProjectIdAndState(projectId, SprintState.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sprintBoardService.getBoard(callerId, "TRK"))
                .isInstanceOf(NoActiveSprintException.class);
    }

    @Test
    void requiresCallerToBeAProjectMember() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenThrow(new NotAProjectMemberException());

        assertThatThrownBy(() -> sprintBoardService.getBoard(callerId, "TRK"))
                .isInstanceOf(NotAProjectMemberException.class);
    }
}
