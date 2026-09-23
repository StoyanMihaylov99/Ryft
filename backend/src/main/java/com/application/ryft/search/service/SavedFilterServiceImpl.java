package com.application.ryft.search.service;

import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.search.dto.CreateSavedFilterRequest;
import com.application.ryft.search.dto.SavedFilterResponse;
import com.application.ryft.search.entity.SavedFilter;
import com.application.ryft.search.exception.InsufficientProjectRoleException;
import com.application.ryft.search.exception.NotSavedFilterOwnerException;
import com.application.ryft.search.exception.SavedFilterNameAlreadyExistsException;
import com.application.ryft.search.exception.SavedFilterNotFoundException;
import com.application.ryft.search.repository.SavedFilterRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedFilterServiceImpl implements SavedFilterService {

    private final SavedFilterRepository savedFilterRepository;
    private final SearchProjectAccess projectAccess;

    public SavedFilterServiceImpl(SavedFilterRepository savedFilterRepository, SearchProjectAccess projectAccess) {
        this.savedFilterRepository = savedFilterRepository;
        this.projectAccess = projectAccess;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedFilterResponse> list(UUID callerId, String projectKey) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        return savedFilterRepository.findAllVisibleToCaller(project.id(), callerId).stream()
                .map(SavedFilterResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public SavedFilterResponse create(UUID callerId, String projectKey, CreateSavedFilterRequest request) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        if (request.isShared()) {
            requireNotViewer(callerId, projectKey);
        }

        String name = request.name().trim();
        requireNameAvailable(project.id(), callerId, name);

        SavedFilter filter = new SavedFilter(project.id(), callerId, name, request.query(), request.isShared());
        return SavedFilterResponse.from(savedFilterRepository.save(filter));
    }

    @Override
    @Transactional
    public void delete(UUID callerId, String projectKey, UUID filterId) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        SavedFilter filter = requireFilter(filterId, project.id());
        requireOwner(callerId, filter);

        savedFilterRepository.deleteByIdAndProjectIdAndOwnerId(filterId, project.id(), callerId);
    }

    private SavedFilter requireFilter(UUID filterId, UUID projectId) {
        return savedFilterRepository.findByIdAndProjectId(filterId, projectId)
                .orElseThrow(SavedFilterNotFoundException::new);
    }

    private void requireOwner(UUID callerId, SavedFilter filter) {
        if (!filter.getOwnerId().equals(callerId)) {
            throw new NotSavedFilterOwnerException();
        }
    }

    /** Viewer is fully read-only: sharing a filter with the whole project is a write with project-wide visibility. */
    private void requireNotViewer(UUID callerId, String projectKey) {
        if (projectAccess.isViewer(callerId, projectKey)) {
            throw new InsufficientProjectRoleException();
        }
    }

    private void requireNameAvailable(UUID projectId, UUID ownerId, String name) {
        if (savedFilterRepository.existsByProjectIdAndOwnerIdAndName(projectId, ownerId, name)) {
            throw new SavedFilterNameAlreadyExistsException(name);
        }
    }
}
