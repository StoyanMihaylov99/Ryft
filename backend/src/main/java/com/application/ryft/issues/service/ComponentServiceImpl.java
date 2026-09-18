package com.application.ryft.issues.service;

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
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComponentServiceImpl implements ComponentService {

    private final ComponentRepository componentRepository;
    private final IssueComponentRepository issueComponentRepository;
    private final IssueProjectAccess projectAccess;

    public ComponentServiceImpl(ComponentRepository componentRepository,
            IssueComponentRepository issueComponentRepository, IssueProjectAccess projectAccess) {
        this.componentRepository = componentRepository;
        this.issueComponentRepository = issueComponentRepository;
        this.projectAccess = projectAccess;
    }

    @Override
    @Transactional
    public ComponentResponse create(UUID callerId, String projectKey, CreateComponentRequest request) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);

        String name = request.name().trim();
        requireNameAvailable(project.id(), name, null);
        return ComponentResponse.from(componentRepository.save(new Component(project.id(), name)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComponentResponse> listForProject(UUID callerId, String projectKey) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        return componentRepository.findAllByProjectIdOrderByNameAsc(project.id()).stream()
                .map(ComponentResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ComponentResponse update(UUID callerId, String projectKey, UUID componentId, UpdateComponentRequest request) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);
        Component component = requireComponent(componentId, project.id());

        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            requireNameAvailable(project.id(), name, component);
            component.setName(name);
        }
        return ComponentResponse.from(component);
    }

    @Override
    @Transactional
    public void delete(UUID callerId, String projectKey, UUID componentId) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);
        Component component = requireComponent(componentId, project.id());

        issueComponentRepository.deleteAllByComponentId(component.getId());
        componentRepository.delete(component);
    }

    /** {@code current} is the component being renamed (excluded from its own duplicate check), or null on create. */
    private void requireNameAvailable(UUID projectId, String name, Component current) {
        boolean unchanged = current != null && name.equals(current.getName());
        if (!unchanged && componentRepository.existsByProjectIdAndName(projectId, name)) {
            throw new ComponentNameAlreadyExistsException(name);
        }
    }

    private Component requireComponent(UUID componentId, UUID projectId) {
        return componentRepository.findByIdAndProjectId(componentId, projectId)
                .orElseThrow(() -> new ComponentNotFoundException(componentId));
    }

    private void requireOwnerOrAdmin(UUID callerId, String projectKey) {
        if (!projectAccess.isOwnerOrAdmin(callerId, projectKey)) {
            throw new InsufficientProjectRoleException();
        }
    }
}
