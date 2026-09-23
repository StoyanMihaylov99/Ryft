package com.application.ryft.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.search.dto.CreateSavedFilterRequest;
import com.application.ryft.search.dto.IssueSearchRequest;
import com.application.ryft.search.dto.SavedFilterResponse;
import com.application.ryft.search.entity.SavedFilter;
import com.application.ryft.search.exception.InsufficientProjectRoleException;
import com.application.ryft.search.exception.NotAProjectMemberException;
import com.application.ryft.search.exception.NotSavedFilterOwnerException;
import com.application.ryft.search.exception.SavedFilterNameAlreadyExistsException;
import com.application.ryft.search.exception.SavedFilterNotFoundException;
import com.application.ryft.search.repository.SavedFilterRepository;
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
class SavedFilterServiceTest {

    @Mock
    private SavedFilterRepository savedFilterRepository;

    @Mock
    private SearchProjectAccess projectAccess;

    private SavedFilterServiceImpl savedFilterService;

    private final UUID callerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final ProjectResponse project = new ProjectResponse(projectId, UUID.randomUUID(), "TRK", "Tracker", null,
            Instant.now(), null);
    private final IssueSearchRequest query = new IssueSearchRequest(List.of(callerId), null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        savedFilterService = new SavedFilterServiceImpl(savedFilterRepository, projectAccess);
    }

    @Test
    void listReturnsOwnFiltersAndOthersSharedOnes() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        SavedFilter own = new SavedFilter(projectId, callerId, "Mine", query, false);
        SavedFilter othersShared = new SavedFilter(projectId, UUID.randomUUID(), "Theirs (shared)", query, true);
        when(savedFilterRepository.findAllVisibleToCaller(projectId, callerId)).thenReturn(List.of(own, othersShared));

        List<SavedFilterResponse> result = savedFilterService.list(callerId, "TRK");

        assertThat(result).extracting(SavedFilterResponse::name).containsExactly("Mine", "Theirs (shared)");
    }

    @Test
    void listRequiresCallerToBeAProjectMember() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenThrow(new NotAProjectMemberException());

        assertThatThrownBy(() -> savedFilterService.list(callerId, "TRK"))
                .isInstanceOf(NotAProjectMemberException.class);
    }

    @Test
    void createPrivateFilterSucceedsForAnyMemberIncludingViewer() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(savedFilterRepository.existsByProjectIdAndOwnerIdAndName(projectId, callerId, "My Bugs")).thenReturn(false);
        when(savedFilterRepository.save(any(SavedFilter.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedFilterResponse result = savedFilterService.create(callerId, "TRK",
                new CreateSavedFilterRequest("My Bugs", query, false));

        assertThat(result.name()).isEqualTo("My Bugs");
        assertThat(result.isShared()).isFalse();
        assertThat(result.ownerId()).isEqualTo(callerId);
        verify(projectAccess, never()).isViewer(any(), any());
    }

    @Test
    void createSharedFilterSucceedsForNonViewer() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isViewer(callerId, "TRK")).thenReturn(false);
        when(savedFilterRepository.existsByProjectIdAndOwnerIdAndName(projectId, callerId, "Team Filter")).thenReturn(false);
        when(savedFilterRepository.save(any(SavedFilter.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedFilterResponse result = savedFilterService.create(callerId, "TRK",
                new CreateSavedFilterRequest("Team Filter", query, true));

        assertThat(result.isShared()).isTrue();
    }

    @Test
    void createSharedFilterRejectsViewer() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(projectAccess.isViewer(callerId, "TRK")).thenReturn(true);

        assertThatThrownBy(() -> savedFilterService.create(callerId, "TRK",
                new CreateSavedFilterRequest("Team Filter", query, true)))
                .isInstanceOf(InsufficientProjectRoleException.class);
        verify(savedFilterRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateNameForTheSameOwner() {
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(savedFilterRepository.existsByProjectIdAndOwnerIdAndName(projectId, callerId, "My Bugs")).thenReturn(true);

        assertThatThrownBy(() -> savedFilterService.create(callerId, "TRK",
                new CreateSavedFilterRequest("My Bugs", query, false)))
                .isInstanceOf(SavedFilterNameAlreadyExistsException.class);
        verify(savedFilterRepository, never()).save(any());
    }

    @Test
    void deleteByOwnerSucceeds() {
        UUID filterId = UUID.randomUUID();
        SavedFilter filter = new SavedFilter(projectId, callerId, "Mine", query, false);
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(savedFilterRepository.findByIdAndProjectId(filterId, projectId)).thenReturn(Optional.of(filter));

        savedFilterService.delete(callerId, "TRK", filterId);

        verify(savedFilterRepository).deleteByIdAndProjectIdAndOwnerId(filterId, projectId, callerId);
    }

    @Test
    void deleteByNonOwnerIsRejected() {
        UUID filterId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        SavedFilter filter = new SavedFilter(projectId, ownerId, "Someone else's", query, true);
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(savedFilterRepository.findByIdAndProjectId(filterId, projectId)).thenReturn(Optional.of(filter));

        assertThatThrownBy(() -> savedFilterService.delete(callerId, "TRK", filterId))
                .isInstanceOf(NotSavedFilterOwnerException.class);
        verify(savedFilterRepository, never()).deleteByIdAndProjectIdAndOwnerId(any(), any(), any());
    }

    @Test
    void deleteOfUnknownOrWrongProjectFilterThrowsNotFound() {
        UUID filterId = UUID.randomUUID();
        when(projectAccess.requireMembership(callerId, "TRK")).thenReturn(project);
        when(savedFilterRepository.findByIdAndProjectId(filterId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savedFilterService.delete(callerId, "TRK", filterId))
                .isInstanceOf(SavedFilterNotFoundException.class);
    }
}
