package com.application.ryft.issues.service;

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
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabelServiceImpl implements LabelService {

    private final LabelRepository labelRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final IssueProjectAccess projectAccess;

    public LabelServiceImpl(LabelRepository labelRepository, IssueLabelRepository issueLabelRepository,
            IssueProjectAccess projectAccess) {
        this.labelRepository = labelRepository;
        this.issueLabelRepository = issueLabelRepository;
        this.projectAccess = projectAccess;
    }

    @Override
    @Transactional
    public LabelResponse create(UUID callerId, String projectKey, CreateLabelRequest request) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);

        String name = request.name().trim();
        requireNameAvailable(project.id(), name, null);
        return LabelResponse.from(labelRepository.save(new Label(project.id(), name, request.color())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabelResponse> listForProject(UUID callerId, String projectKey) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        return labelRepository.findAllByProjectIdOrderByNameAsc(project.id()).stream()
                .map(LabelResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public LabelResponse update(UUID callerId, String projectKey, UUID labelId, UpdateLabelRequest request) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);
        Label label = requireLabel(labelId, project.id());

        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            requireNameAvailable(project.id(), name, label);
            label.setName(name);
        }
        if (request.color() != null) {
            label.setColor(request.color());
        }
        return LabelResponse.from(label);
    }

    @Override
    @Transactional
    public void delete(UUID callerId, String projectKey, UUID labelId) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);
        Label label = requireLabel(labelId, project.id());

        issueLabelRepository.deleteAllByLabelId(label.getId());
        labelRepository.delete(label);
    }

    /** {@code current} is the label being renamed (excluded from its own duplicate check), or null on create. */
    private void requireNameAvailable(UUID projectId, String name, Label current) {
        boolean unchanged = current != null && name.equals(current.getName());
        if (!unchanged && labelRepository.existsByProjectIdAndName(projectId, name)) {
            throw new LabelNameAlreadyExistsException(name);
        }
    }

    private Label requireLabel(UUID labelId, UUID projectId) {
        return labelRepository.findByIdAndProjectId(labelId, projectId)
                .orElseThrow(() -> new LabelNotFoundException(labelId));
    }

    private void requireOwnerOrAdmin(UUID callerId, String projectKey) {
        if (!projectAccess.isOwnerOrAdmin(callerId, projectKey)) {
            throw new InsufficientProjectRoleException();
        }
    }
}
