package com.application.ryft.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import com.application.ryft.workflow.entity.WorkflowScheme;
import com.application.ryft.workflow.entity.WorkflowStatus;
import com.application.ryft.workflow.exception.NotAProjectMemberException;
import com.application.ryft.workflow.repository.WorkflowSchemeRepository;
import com.application.ryft.workflow.repository.WorkflowStatusRepository;
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
class WorkflowServiceTest {

    @Mock
    private WorkflowSchemeRepository workflowSchemeRepository;

    @Mock
    private WorkflowStatusRepository workflowStatusRepository;

    @Mock
    private ProjectAccess projectAccess;

    private WorkflowServiceImpl workflowService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);

    @BeforeEach
    void setUp() {
        workflowService = new WorkflowServiceImpl(workflowSchemeRepository, workflowStatusRepository, projectAccess);
    }

    @Test
    void createsDefaultSchemeWhenNoneExistsYet() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        WorkflowScheme savedScheme = new WorkflowScheme(projectId, "Default Workflow");
        when(workflowSchemeRepository.save(any(WorkflowScheme.class))).thenReturn(savedScheme);
        when(workflowStatusRepository.save(any(WorkflowStatus.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(any())).thenReturn(List.of(
                new WorkflowStatus(savedScheme, "To Do", StatusCategory.TODO, 0),
                new WorkflowStatus(savedScheme, "In Progress", StatusCategory.IN_PROGRESS, 1),
                new WorkflowStatus(savedScheme, "Done", StatusCategory.DONE, 2)));

        WorkflowSchemeResponse result = workflowService.getSchemeForProject(callerId, "TRK");

        assertThat(result.projectId()).isEqualTo(projectId);
        assertThat(result.statuses()).hasSize(3);
        assertThat(result.statuses().get(0).name()).isEqualTo("To Do");
        assertThat(result.statuses().get(0).category()).isEqualTo(StatusCategory.TODO);
        assertThat(result.statuses().get(1).name()).isEqualTo("In Progress");
        assertThat(result.statuses().get(2).name()).isEqualTo("Done");
        verify(workflowStatusRepository, times(3)).save(any(WorkflowStatus.class));
    }

    @Test
    void returnsExistingSchemeWithoutCreatingANewOne() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        WorkflowScheme existing = new WorkflowScheme(projectId, "Default Workflow");
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(existing));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(any())).thenReturn(List.of(
                new WorkflowStatus(existing, "To Do", StatusCategory.TODO, 0)));

        WorkflowSchemeResponse result = workflowService.getSchemeForProject(callerId, "TRK");

        assertThat(result.statuses()).hasSize(1);
        verify(workflowSchemeRepository, never()).save(any());
    }

    @Test
    void propagatesNotAProjectMember() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenThrow(new NotAProjectMemberException());

        assertThatThrownBy(() -> workflowService.getSchemeForProject(callerId, "TRK"))
                .isInstanceOf(NotAProjectMemberException.class);
        verify(workflowSchemeRepository, never()).findByProjectId(any());
    }
}
