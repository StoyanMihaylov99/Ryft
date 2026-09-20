package com.application.ryft.issues.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.issues.dto.ComponentResponse;
import com.application.ryft.issues.dto.CreateComponentRequest;
import com.application.ryft.issues.dto.UpdateComponentRequest;
import com.application.ryft.issues.entity.Component;
import com.application.ryft.issues.exception.ComponentNameAlreadyExistsException;
import com.application.ryft.issues.exception.ComponentNotFoundException;
import com.application.ryft.issues.exception.InsufficientProjectRoleException;
import com.application.ryft.issues.repository.ComponentRepository;
import com.application.ryft.issues.repository.IssueComponentRepository;
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
class ComponentServiceImplTest {

    @Mock
    private ComponentRepository componentRepository;

    @Mock
    private IssueComponentRepository issueComponentRepository;

    @Mock
    private IssueProjectAccess projectAccess;

    private ComponentServiceImpl componentService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);

    @BeforeEach
    void setUp() {
        componentService = new ComponentServiceImpl(componentRepository, issueComponentRepository, projectAccess);
    }

    private Component componentWithId(UUID projectId, String name) {
        Component component = new Component(projectId, name);
        ReflectionTestUtils.setField(component, "id", UUID.randomUUID());
        return component;
    }

    @Test
    void createSavesComponentWhenNameIsAvailable() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(componentRepository.existsByProjectIdAndName(projectId, "Backend")).thenReturn(false);
        when(componentRepository.save(any(Component.class))).thenAnswer(inv -> inv.getArgument(0));

        ComponentResponse result = componentService.create(callerId, "TRK", new CreateComponentRequest("Backend"));

        assertThat(result.name()).isEqualTo("Backend");
    }

    @Test
    void createRejectsDuplicateNameInSameProject() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(componentRepository.existsByProjectIdAndName(projectId, "Backend")).thenReturn(true);

        assertThatThrownBy(() -> componentService.create(callerId, "TRK", new CreateComponentRequest("Backend")))
                .isInstanceOf(ComponentNameAlreadyExistsException.class);
        verify(componentRepository, never()).save(any());
    }

    @Test
    void createRejectsCallerWhoIsNotOwnerOrAdmin() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> componentService.create(callerId, "TRK", new CreateComponentRequest("Backend")))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(componentRepository, never()).save(any());
    }

    @Test
    void listForProjectReturnsComponentsOrderedByName() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        Component component = componentWithId(projectId, "Backend");
        when(componentRepository.findAllByProjectIdOrderByNameAsc(projectId)).thenReturn(List.of(component));

        List<ComponentResponse> result = componentService.listForProject(callerId, "TRK");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Backend");
    }

    @Test
    void updateRenamesComponentWhenNewNameIsAvailable() {
        Component component = componentWithId(projectId, "Backend");
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(componentRepository.findByIdAndProjectId(component.getId(), projectId)).thenReturn(Optional.of(component));
        when(componentRepository.existsByProjectIdAndName(projectId, "API")).thenReturn(false);

        ComponentResponse result = componentService.update(callerId, "TRK", component.getId(),
                new UpdateComponentRequest("API"));

        assertThat(result.name()).isEqualTo("API");
    }

    @Test
    void updateRejectsRenameToAnAlreadyUsedName() {
        Component component = componentWithId(projectId, "Backend");
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(componentRepository.findByIdAndProjectId(component.getId(), projectId)).thenReturn(Optional.of(component));
        when(componentRepository.existsByProjectIdAndName(projectId, "API")).thenReturn(true);

        assertThatThrownBy(() -> componentService.update(callerId, "TRK", component.getId(),
                new UpdateComponentRequest("API")))
                .isInstanceOf(ComponentNameAlreadyExistsException.class);
    }

    @Test
    void updateRequiresComponentToExistInProject() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        UUID componentId = UUID.randomUUID();
        when(componentRepository.findByIdAndProjectId(componentId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> componentService.update(callerId, "TRK", componentId,
                new UpdateComponentRequest("API")))
                .isInstanceOf(ComponentNotFoundException.class);
    }

    @Test
    void deleteRemovesComponentAndItsJoinRows() {
        Component component = componentWithId(projectId, "Backend");
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(true);
        when(componentRepository.findByIdAndProjectId(component.getId(), projectId)).thenReturn(Optional.of(component));

        componentService.delete(callerId, "TRK", component.getId());

        verify(issueComponentRepository).deleteAllByComponentId(component.getId());
        verify(componentRepository).delete(component);
    }

    @Test
    void deleteRejectsCallerWhoIsNotOwnerOrAdmin() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isOwnerOrAdmin(callerId, "TRK")).thenReturn(false);

        assertThatThrownBy(() -> componentService.delete(callerId, "TRK", UUID.randomUUID()))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(componentRepository, never()).delete(any());
    }
}
