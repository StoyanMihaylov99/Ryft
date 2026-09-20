package com.application.ryft.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.issues.service.IssueService;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.workflow.dto.UpdateWorkflowSchemeRequest;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusEdit;
import com.application.ryft.workflow.dto.WorkflowTransitionEdit;
import com.application.ryft.workflow.entity.StatusCategory;
import com.application.ryft.workflow.entity.WorkflowScheme;
import com.application.ryft.workflow.entity.WorkflowStatus;
import com.application.ryft.workflow.entity.WorkflowTransition;
import com.application.ryft.workflow.exception.DuplicateWorkflowTransitionException;
import com.application.ryft.workflow.exception.InsufficientProjectRoleException;
import com.application.ryft.workflow.exception.InvalidWorkflowStatusReferenceException;
import com.application.ryft.workflow.exception.NotAProjectMemberException;
import com.application.ryft.workflow.exception.WorkflowStatusInUseException;
import com.application.ryft.workflow.repository.WorkflowSchemeRepository;
import com.application.ryft.workflow.repository.WorkflowStatusRepository;
import com.application.ryft.workflow.repository.WorkflowTransitionRepository;
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
    private WorkflowTransitionRepository workflowTransitionRepository;

    @Mock
    private WorkflowProjectAccess projectAccess;

    @Mock
    private IssueService issueService;

    private WorkflowServiceImpl workflowService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);

    @BeforeEach
    void setUp() {
        workflowService = new WorkflowServiceImpl(workflowSchemeRepository, workflowStatusRepository,
                workflowTransitionRepository, projectAccess, issueService);
        lenient().when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        // Mimics Hibernate's @UuidGenerator assigning an id at persist time — plain Mockito stubs
        // otherwise hand back the still-id-less argument, and this class's production code (unlike
        // before this phase) now compares WorkflowStatus/WorkflowTransition ids directly.
        lenient().when(workflowStatusRepository.save(any(WorkflowStatus.class))).thenAnswer(inv -> {
            WorkflowStatus saved = inv.getArgument(0);
            if (saved.getId() == null) {
                withReflectedId(saved, UUID.randomUUID());
            }
            return saved;
        });
        lenient().when(workflowTransitionRepository.save(any(WorkflowTransition.class))).thenAnswer(inv -> {
            WorkflowTransition saved = inv.getArgument(0);
            if (saved.getId() == null) {
                org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            }
            return saved;
        });
    }

    @Test
    void createsDefaultSchemeWhenNoneExistsYet() {
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        WorkflowScheme savedScheme = new WorkflowScheme(projectId, "Default Workflow");
        when(workflowSchemeRepository.save(any(WorkflowScheme.class))).thenReturn(savedScheme);
        WorkflowStatus todo = new WorkflowStatus(savedScheme, "To Do", StatusCategory.TODO, 0);
        WorkflowStatus blocked = new WorkflowStatus(savedScheme, "Blocked", StatusCategory.BLOCKED, 1);
        WorkflowStatus inProgress = new WorkflowStatus(savedScheme, "In Progress", StatusCategory.IN_PROGRESS, 2);
        WorkflowStatus done = new WorkflowStatus(savedScheme, "Done", StatusCategory.DONE, 3);
        withReflectedId(todo, UUID.randomUUID());
        withReflectedId(blocked, UUID.randomUUID());
        withReflectedId(inProgress, UUID.randomUUID());
        withReflectedId(done, UUID.randomUUID());
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(any()))
                .thenReturn(List.of(todo, blocked, inProgress, done));

        WorkflowSchemeResponse result = workflowService.getSchemeForProject(callerId, "TRK");

        assertThat(result.projectId()).isEqualTo(projectId);
        assertThat(result.statuses()).hasSize(4);
        assertThat(result.statuses().get(0).name()).isEqualTo("To Do");
        assertThat(result.statuses().get(0).category()).isEqualTo(StatusCategory.TODO);
        assertThat(result.statuses().get(1).name()).isEqualTo("Blocked");
        assertThat(result.statuses().get(2).name()).isEqualTo("In Progress");
        assertThat(result.statuses().get(3).name()).isEqualTo("Done");
        verify(workflowStatusRepository, times(4)).save(any(WorkflowStatus.class));
    }

    @Test
    void returnsExistingSchemeWithoutCreatingANewOne() {
        WorkflowScheme existing = new WorkflowScheme(projectId, "Default Workflow");
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(existing));
        WorkflowStatus todo = new WorkflowStatus(existing, "To Do", StatusCategory.TODO, 0);
        WorkflowStatus blocked = new WorkflowStatus(existing, "Blocked", StatusCategory.BLOCKED, 1);
        withReflectedId(todo, UUID.randomUUID());
        withReflectedId(blocked, UUID.randomUUID());
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(any()))
                .thenReturn(List.of(todo, blocked));

        WorkflowSchemeResponse result = workflowService.getSchemeForProject(callerId, "TRK");

        assertThat(result.statuses()).hasSize(2);
        verify(workflowSchemeRepository, never()).save(any());
    }

    @Test
    void backfillsBlockedStatusForASchemeCreatedBeforeItExisted() {
        WorkflowScheme existing = new WorkflowScheme(projectId, "Default Workflow");
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(existing));
        WorkflowStatus todo = new WorkflowStatus(existing, "To Do", StatusCategory.TODO, 0);
        WorkflowStatus inProgress = new WorkflowStatus(existing, "In Progress", StatusCategory.IN_PROGRESS, 1);
        WorkflowStatus done = new WorkflowStatus(existing, "Done", StatusCategory.DONE, 2);
        withReflectedId(todo, UUID.randomUUID());
        withReflectedId(inProgress, UUID.randomUUID());
        withReflectedId(done, UUID.randomUUID());
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(any()))
                .thenReturn(List.of(todo, inProgress, done));

        WorkflowSchemeResponse result = workflowService.getSchemeForProject(callerId, "TRK");

        assertThat(result.statuses()).hasSize(4);
        assertThat(result.statuses().get(0).name()).isEqualTo("To Do");
        assertThat(result.statuses().get(0).sortOrder()).isEqualTo(0);
        assertThat(result.statuses().get(1).name()).isEqualTo("Blocked");
        assertThat(result.statuses().get(1).category()).isEqualTo(StatusCategory.BLOCKED);
        assertThat(result.statuses().get(1).sortOrder()).isEqualTo(1);
        assertThat(result.statuses().get(2).name()).isEqualTo("In Progress");
        assertThat(result.statuses().get(2).sortOrder()).isEqualTo(2);
        assertThat(result.statuses().get(3).name()).isEqualTo("Done");
        assertThat(result.statuses().get(3).sortOrder()).isEqualTo(3);
        verify(workflowStatusRepository, times(1)).save(any(WorkflowStatus.class));
        verify(workflowSchemeRepository, never()).save(any());
    }

    @Test
    void propagatesNotAProjectMember() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenThrow(new NotAProjectMemberException());

        assertThatThrownBy(() -> workflowService.getSchemeForProject(callerId, "TRK"))
                .isInstanceOf(NotAProjectMemberException.class);
        verify(workflowSchemeRepository, never()).findByProjectId(any());
    }

    /** Includes a Blocked status explicitly so {@code backfillBlockedStatusIfMissing} is a no-op and doesn't change the status count mid-test. */
    @Test
    void getSchemeForProjectBackfillsAnyToAnyTransitionsWhenNoneExist() {
        WorkflowScheme existing = new WorkflowScheme(projectId, "Default Workflow");
        WorkflowStatus todo = new WorkflowStatus(existing, "To Do", StatusCategory.TODO, 0);
        WorkflowStatus blocked = new WorkflowStatus(existing, "Blocked", StatusCategory.BLOCKED, 1);
        WorkflowStatus done = new WorkflowStatus(existing, "Done", StatusCategory.DONE, 2);
        withReflectedId(todo, UUID.randomUUID());
        withReflectedId(blocked, UUID.randomUUID());
        withReflectedId(done, UUID.randomUUID());
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(existing));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(any()))
                .thenReturn(List.of(todo, blocked, done));
        when(workflowTransitionRepository.findAllByWorkflowSchemeId(any())).thenReturn(List.of());

        WorkflowSchemeResponse result = workflowService.getSchemeForProject(callerId, "TRK");

        // any-to-any over 3 statuses = exactly 6 directed pairs
        assertThat(result.transitions()).hasSize(6);
        verify(workflowTransitionRepository, times(6)).save(any(WorkflowTransition.class));
    }

    @Test
    void getSchemeForProjectDoesNotReseedTransitionsWhenSomeAlreadyExist() {
        WorkflowScheme existing = new WorkflowScheme(projectId, "Default Workflow");
        WorkflowStatus todo = new WorkflowStatus(existing, "To Do", StatusCategory.TODO, 0);
        WorkflowStatus done = new WorkflowStatus(existing, "Done", StatusCategory.DONE, 1);
        withReflectedId(todo, UUID.randomUUID());
        withReflectedId(done, UUID.randomUUID());
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(existing));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(any()))
                .thenReturn(List.of(todo, done));
        when(workflowTransitionRepository.findAllByWorkflowSchemeId(any()))
                .thenReturn(List.of(new WorkflowTransition(existing, todo, done, "To Do → Done")));

        WorkflowSchemeResponse result = workflowService.getSchemeForProject(callerId, "TRK");

        assertThat(result.transitions()).hasSize(1);
        verify(workflowTransitionRepository, never()).save(any());
    }

    @Test
    void isTransitionLegalIsAlwaysTrueForANoOpMove() {
        UUID statusId = UUID.randomUUID();

        assertThat(workflowService.isTransitionLegal(callerId, "TRK", statusId, statusId)).isTrue();
        verify(projectAccess, never()).requireMembership(any(), any());
    }

    @Test
    void isTransitionLegalTrueWhenTransitionExists() {
        WorkflowScheme existing = new WorkflowScheme(projectId, "Default Workflow");
        WorkflowStatus from = new WorkflowStatus(existing, "To Do", StatusCategory.TODO, 0);
        WorkflowStatus to = new WorkflowStatus(existing, "Done", StatusCategory.DONE, 1);
        withReflectedId(from, UUID.randomUUID());
        withReflectedId(to, UUID.randomUUID());
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(existing));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(any()))
                .thenReturn(List.of(from, to));
        when(workflowTransitionRepository.findAllByWorkflowSchemeId(any()))
                .thenReturn(List.of(new WorkflowTransition(existing, from, to, "To Do → Done")));

        assertThat(workflowService.isTransitionLegal(callerId, "TRK", from.getId(), to.getId())).isTrue();
    }

    /**
     * A truly empty transition list would itself get lazily any-to-any-backfilled on this very read (see
     * {@link WorkflowServiceImpl#backfillTransitionsIfMissing}), so testing "illegal" requires the graph
     * to already be non-empty — otherwise there's no way to observe an unseeded pair as illegal.
     */
    @Test
    void isTransitionLegalFalseWhenNoSuchTransitionDefined() {
        WorkflowScheme existing = new WorkflowScheme(projectId, "Default Workflow");
        WorkflowStatus todo = new WorkflowStatus(existing, "To Do", StatusCategory.TODO, 0);
        WorkflowStatus blocked = new WorkflowStatus(existing, "Blocked", StatusCategory.BLOCKED, 1);
        WorkflowStatus done = new WorkflowStatus(existing, "Done", StatusCategory.DONE, 2);
        withReflectedId(todo, UUID.randomUUID());
        withReflectedId(blocked, UUID.randomUUID());
        withReflectedId(done, UUID.randomUUID());
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(existing));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(any()))
                .thenReturn(List.of(todo, blocked, done));
        when(workflowTransitionRepository.findAllByWorkflowSchemeId(any()))
                .thenReturn(List.of(new WorkflowTransition(existing, blocked, done, "Blocked → Done")));

        assertThat(workflowService.isTransitionLegal(callerId, "TRK", todo.getId(), done.getId())).isFalse();
    }

    @Test
    void updateSchemeRejectsCallerWhoIsNotOwnerOrAdmin() {
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> workflowService.updateScheme(callerId, "TRK",
                new UpdateWorkflowSchemeRequest(List.of(), List.of())))
                .isInstanceOf(InsufficientProjectRoleException.class);
    }

    @Test
    void updateSchemeCreatesAndRenamesStatuses() {
        WorkflowScheme scheme = new WorkflowScheme(projectId, "Default Workflow");
        WorkflowStatus existingStatus = new WorkflowStatus(scheme, "To Do", StatusCategory.TODO, 0);
        withReflectedId(existingStatus, UUID.randomUUID());
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(scheme));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(scheme.getId()))
                .thenReturn(List.of(existingStatus));
        when(workflowTransitionRepository.findAllByWorkflowSchemeId(scheme.getId())).thenReturn(List.of());
        WorkflowStatus createdStatus = new WorkflowStatus(scheme, "Done", StatusCategory.DONE, 1);
        withReflectedId(createdStatus, UUID.randomUUID());
        when(workflowStatusRepository.save(any(WorkflowStatus.class))).thenReturn(createdStatus);

        WorkflowSchemeResponse result = workflowService.updateScheme(callerId, "TRK",
                new UpdateWorkflowSchemeRequest(
                        List.of(new WorkflowStatusEdit(existingStatus.getId(), "Backlog", StatusCategory.TODO, 0),
                                new WorkflowStatusEdit(null, "Done", StatusCategory.DONE, 1)),
                        List.of()));

        assertThat(result.statuses()).extracting("name").containsExactlyInAnyOrder("Backlog", "Done");
        assertThat(existingStatus.getName()).isEqualTo("Backlog");
    }

    @Test
    void updateSchemeDeletingAnInUseStatusThrows() {
        WorkflowScheme scheme = new WorkflowScheme(projectId, "Default Workflow");
        WorkflowStatus existingStatus = new WorkflowStatus(scheme, "To Do", StatusCategory.TODO, 0);
        withReflectedId(existingStatus, UUID.randomUUID());
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(scheme));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(scheme.getId()))
                .thenReturn(List.of(existingStatus));
        when(issueService.existsAnyWithWorkflowStatusId(projectId, existingStatus.getId())).thenReturn(true);

        assertThatThrownBy(() -> workflowService.updateScheme(callerId, "TRK",
                new UpdateWorkflowSchemeRequest(List.of(), List.of())))
                .isInstanceOf(WorkflowStatusInUseException.class);
        verify(workflowStatusRepository, never()).delete(any());
    }

    @Test
    void updateSchemeDeletingAnUnusedStatusSucceeds() {
        WorkflowScheme scheme = new WorkflowScheme(projectId, "Default Workflow");
        WorkflowStatus existingStatus = new WorkflowStatus(scheme, "To Do", StatusCategory.TODO, 0);
        withReflectedId(existingStatus, UUID.randomUUID());
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(scheme));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(scheme.getId()))
                .thenReturn(List.of(existingStatus));
        when(issueService.existsAnyWithWorkflowStatusId(projectId, existingStatus.getId())).thenReturn(false);
        when(workflowTransitionRepository.findAllByWorkflowSchemeId(scheme.getId())).thenReturn(List.of());

        WorkflowSchemeResponse result = workflowService.updateScheme(callerId, "TRK",
                new UpdateWorkflowSchemeRequest(List.of(), List.of()));

        assertThat(result.statuses()).isEmpty();
        verify(workflowStatusRepository).delete(existingStatus);
        verify(workflowTransitionRepository).deleteAllReferencingStatus(existingStatus.getId());
    }

    @Test
    void updateSchemeTransitionReferencingUnknownStatusThrows() {
        WorkflowScheme scheme = new WorkflowScheme(projectId, "Default Workflow");
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(scheme));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(scheme.getId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> workflowService.updateScheme(callerId, "TRK",
                new UpdateWorkflowSchemeRequest(List.of(),
                        List.of(new WorkflowTransitionEdit(null, UUID.randomUUID(), UUID.randomUUID(), "Go")))))
                .isInstanceOf(InvalidWorkflowStatusReferenceException.class);
    }

    @Test
    void updateSchemeRejectsDuplicateTransitionWithinTheSameRequest() {
        WorkflowScheme scheme = new WorkflowScheme(projectId, "Default Workflow");
        WorkflowStatus from = new WorkflowStatus(scheme, "To Do", StatusCategory.TODO, 0);
        WorkflowStatus to = new WorkflowStatus(scheme, "Done", StatusCategory.DONE, 1);
        withReflectedId(from, UUID.randomUUID());
        withReflectedId(to, UUID.randomUUID());
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(scheme));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(scheme.getId()))
                .thenReturn(List.of(from, to));
        WorkflowTransition createdTransition = new WorkflowTransition(scheme, from, to, "Go");
        org.springframework.test.util.ReflectionTestUtils.setField(createdTransition, "id", UUID.randomUUID());
        lenient().when(workflowTransitionRepository.save(any(WorkflowTransition.class))).thenReturn(createdTransition);

        assertThatThrownBy(() -> workflowService.updateScheme(callerId, "TRK",
                new UpdateWorkflowSchemeRequest(
                        List.of(new WorkflowStatusEdit(from.getId(), "To Do", StatusCategory.TODO, 0),
                                new WorkflowStatusEdit(to.getId(), "Done", StatusCategory.DONE, 1)),
                        List.of(new WorkflowTransitionEdit(null, from.getId(), to.getId(), "Go"),
                                new WorkflowTransitionEdit(null, from.getId(), to.getId(), "Go again")))))
                .isInstanceOf(DuplicateWorkflowTransitionException.class);
    }

    @Test
    void updateSchemeRejectsTransitionDuplicatingAnExistingOne() {
        WorkflowScheme scheme = new WorkflowScheme(projectId, "Default Workflow");
        WorkflowStatus from = new WorkflowStatus(scheme, "To Do", StatusCategory.TODO, 0);
        WorkflowStatus to = new WorkflowStatus(scheme, "Done", StatusCategory.DONE, 1);
        withReflectedId(from, UUID.randomUUID());
        withReflectedId(to, UUID.randomUUID());
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(scheme));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(scheme.getId()))
                .thenReturn(List.of(from, to));
        when(workflowTransitionRepository.findAllByWorkflowSchemeId(scheme.getId())).thenReturn(List.of());
        when(workflowTransitionRepository.existsByWorkflowSchemeIdAndFromStatusIdAndToStatusId(scheme.getId(),
                from.getId(), to.getId())).thenReturn(true);

        assertThatThrownBy(() -> workflowService.updateScheme(callerId, "TRK",
                new UpdateWorkflowSchemeRequest(
                        List.of(new WorkflowStatusEdit(from.getId(), "To Do", StatusCategory.TODO, 0),
                                new WorkflowStatusEdit(to.getId(), "Done", StatusCategory.DONE, 1)),
                        List.of(new WorkflowTransitionEdit(null, from.getId(), to.getId(), "Go")))))
                .isInstanceOf(DuplicateWorkflowTransitionException.class);
    }

    @Test
    void getStatusIdsInCategoryReturnsMatchingStatusIds() {
        WorkflowScheme existing = new WorkflowScheme(projectId, "Default Workflow");
        WorkflowStatus done1 = new WorkflowStatus(existing, "Done", StatusCategory.DONE, 1);
        WorkflowStatus done2 = new WorkflowStatus(existing, "Shipped", StatusCategory.DONE, 2);
        WorkflowStatus todo = new WorkflowStatus(existing, "To Do", StatusCategory.TODO, 0);
        withReflectedId(done1, UUID.randomUUID());
        withReflectedId(done2, UUID.randomUUID());
        withReflectedId(todo, UUID.randomUUID());
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(existing));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(any()))
                .thenReturn(List.of(todo, done1, done2));
        when(workflowTransitionRepository.findAllByWorkflowSchemeId(any()))
                .thenReturn(List.of(new WorkflowTransition(existing, todo, done1, "Go")));

        List<UUID> result = workflowService.getStatusIdsInCategory(callerId, "TRK", StatusCategory.DONE);

        assertThat(result).containsExactlyInAnyOrder(done1.getId(), done2.getId());
    }

    @Test
    void getStatusReturnsMatchingStatus() {
        WorkflowScheme existing = new WorkflowScheme(projectId, "Default Workflow");
        WorkflowStatus todo = new WorkflowStatus(existing, "To Do", StatusCategory.TODO, 0);
        withReflectedId(todo, UUID.randomUUID());
        when(workflowSchemeRepository.findByProjectId(projectId)).thenReturn(Optional.of(existing));
        when(workflowStatusRepository.findAllByWorkflowSchemeIdOrderBySortOrderAsc(any())).thenReturn(List.of(todo));
        when(workflowTransitionRepository.findAllByWorkflowSchemeId(any())).thenReturn(List.of());

        var result = workflowService.getStatus(callerId, "TRK", todo.getId());

        assertThat(result.name()).isEqualTo("To Do");
        assertThat(result.category()).isEqualTo(StatusCategory.TODO);
    }

    private void withReflectedId(WorkflowStatus status, UUID id) {
        org.springframework.test.util.ReflectionTestUtils.setField(status, "id", id);
    }
}
