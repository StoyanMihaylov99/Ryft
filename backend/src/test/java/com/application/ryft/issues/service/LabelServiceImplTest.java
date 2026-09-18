package com.application.ryft.issues.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.issues.dto.CreateLabelRequest;
import com.application.ryft.issues.dto.LabelResponse;
import com.application.ryft.issues.dto.UpdateLabelRequest;
import com.application.ryft.issues.entity.Label;
import com.application.ryft.issues.exception.InsufficientProjectRoleException;
import com.application.ryft.issues.exception.LabelNameAlreadyExistsException;
import com.application.ryft.issues.exception.LabelNotFoundException;
import com.application.ryft.issues.repository.IssueLabelRepository;
import com.application.ryft.issues.repository.LabelRepository;
import com.application.ryft.projects.dto.ProjectResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LabelServiceImplTest {

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private IssueLabelRepository issueLabelRepository;

    @Mock
    private IssueProjectAccess projectAccess;

    private LabelServiceImpl labelService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);

    @BeforeEach
    void setUp() {
        labelService = new LabelServiceImpl(labelRepository, issueLabelRepository, projectAccess);
    }

    private Label labelWithId(UUID projectId, String name, String color) {
        Label label = new Label(projectId, name, color);
        ReflectionTestUtils.setField(label, "id", UUID.randomUUID());
        return label;
    }

    @Test
    void createSavesLabelWhenNameIsAvailable() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(labelRepository.existsByProjectIdAndName(projectId, "Bug")).thenReturn(false);
        when(labelRepository.save(any(Label.class))).thenAnswer(inv -> inv.getArgument(0));

        LabelResponse result = labelService.create(callerId, "TRK", new CreateLabelRequest("Bug", "#FF0000"));

        assertThat(result.name()).isEqualTo("Bug");
        assertThat(result.color()).isEqualTo("#FF0000");
    }

    @Test
    void createRejectsDuplicateNameInSameProject() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(labelRepository.existsByProjectIdAndName(projectId, "Bug")).thenReturn(true);

        assertThatThrownBy(() -> labelService.create(callerId, "TRK", new CreateLabelRequest("Bug", "#FF0000")))
                .isInstanceOf(LabelNameAlreadyExistsException.class);
        verify(labelRepository, never()).save(any());
    }

    @Test
    void createRejectsCallerWhoIsNotOwnerOrAdmin() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> labelService.create(callerId, "TRK", new CreateLabelRequest("Bug", "#FF0000")))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(labelRepository, never()).save(any());
    }

    @Test
    void listForProjectReturnsLabelsOrderedByName() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        Label label = labelWithId(projectId, "Bug", "#FF0000");
        when(labelRepository.findAllByProjectIdOrderByNameAsc(projectId)).thenReturn(List.of(label));

        List<LabelResponse> result = labelService.listForProject(callerId, "TRK");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Bug");
    }

    @Test
    void updateRenamesLabelWhenNewNameIsAvailable() {
        Label label = labelWithId(projectId, "Bug", "#FF0000");
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(labelRepository.findByIdAndProjectId(label.getId(), projectId)).thenReturn(Optional.of(label));
        when(labelRepository.existsByProjectIdAndName(projectId, "Defect")).thenReturn(false);

        LabelResponse result = labelService.update(callerId, "TRK", label.getId(),
                new UpdateLabelRequest("Defect", null));

        assertThat(result.name()).isEqualTo("Defect");
        assertThat(result.color()).isEqualTo("#FF0000");
    }

    @Test
    void updateKeepingTheSameNameDoesNotTriggerDuplicateCheck() {
        Label label = labelWithId(projectId, "Bug", "#FF0000");
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(labelRepository.findByIdAndProjectId(label.getId(), projectId)).thenReturn(Optional.of(label));

        LabelResponse result = labelService.update(callerId, "TRK", label.getId(),
                new UpdateLabelRequest("Bug", "#00FF00"));

        assertThat(result.color()).isEqualTo("#00FF00");
        verify(labelRepository, never()).existsByProjectIdAndName(any(), any());
    }

    @Test
    void updateRejectsRenameToAnAlreadyUsedName() {
        Label label = labelWithId(projectId, "Bug", "#FF0000");
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(labelRepository.findByIdAndProjectId(label.getId(), projectId)).thenReturn(Optional.of(label));
        when(labelRepository.existsByProjectIdAndName(projectId, "Defect")).thenReturn(true);

        assertThatThrownBy(() -> labelService.update(callerId, "TRK", label.getId(),
                new UpdateLabelRequest("Defect", null)))
                .isInstanceOf(LabelNameAlreadyExistsException.class);
    }

    @Test
    void updateRequiresLabelToExistInProject() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        UUID labelId = UUID.randomUUID();
        when(labelRepository.findByIdAndProjectId(labelId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.update(callerId, "TRK", labelId, new UpdateLabelRequest("Defect", null)))
                .isInstanceOf(LabelNotFoundException.class);
    }

    @Test
    void deleteRemovesLabelAndItsJoinRows() {
        Label label = labelWithId(projectId, "Bug", "#FF0000");
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(labelRepository.findByIdAndProjectId(label.getId(), projectId)).thenReturn(Optional.of(label));

        labelService.delete(callerId, "TRK", label.getId());

        verify(issueLabelRepository).deleteAllByLabelId(label.getId());
        verify(labelRepository).delete(label);
    }

    @Test
    void deleteRejectsCallerWhoIsNotOwnerOrAdmin() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> labelService.delete(callerId, "TRK", UUID.randomUUID()))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(labelRepository, never()).delete(any());
    }
}
