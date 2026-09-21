package com.application.ryft.sprints.service;

import com.application.ryft.common.event.SprintCompletedEvent;
import com.application.ryft.common.event.SprintStartedEvent;
import com.application.ryft.issues.service.IssueService;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.sprints.dto.CreateSprintRequest;
import com.application.ryft.sprints.dto.SprintResponse;
import com.application.ryft.sprints.dto.UpdateSprintRequest;
import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.entity.SprintState;
import com.application.ryft.sprints.exception.ActiveSprintAlreadyExistsException;
import com.application.ryft.sprints.exception.IllegalSprintStateTransitionException;
import com.application.ryft.sprints.exception.InsufficientProjectRoleException;
import com.application.ryft.sprints.exception.InvalidSprintDateRangeException;
import com.application.ryft.sprints.exception.SprintCompletedException;
import com.application.ryft.sprints.exception.SprintDatesRequiredException;
import com.application.ryft.sprints.repository.SprintRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SprintServiceImpl implements SprintService {

    private final SprintRepository sprintRepository;
    private final SprintsProjectAccess projectAccess;
    private final SprintLookupSupport sprintLookupSupport;
    private final IssueService issueService;
    private final ApplicationEventPublisher eventPublisher;

    public SprintServiceImpl(SprintRepository sprintRepository, SprintsProjectAccess projectAccess,
            SprintLookupSupport sprintLookupSupport, IssueService issueService,
            ApplicationEventPublisher eventPublisher) {
        this.sprintRepository = sprintRepository;
        this.projectAccess = projectAccess;
        this.sprintLookupSupport = sprintLookupSupport;
        this.issueService = issueService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public SprintResponse create(UUID callerId, String projectKey, CreateSprintRequest request) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);
        validateDateRange(request.startDate(), request.endDate());

        String goal = request.goal() == null ? null : request.goal().trim();
        Sprint sprint = new Sprint(project.id(), request.name().trim(), goal, request.startDate(), request.endDate());
        return SprintResponse.from(sprintRepository.save(sprint));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SprintResponse> listForProject(UUID callerId, String projectKey) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        return sprintRepository.findAllByProjectIdOrderByCreatedAtAsc(project.id()).stream()
                .map(SprintResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public SprintResponse update(UUID callerId, UUID sprintId, UpdateSprintRequest request) {
        Sprint sprint = sprintLookupSupport.requireSprint(sprintId);
        ProjectResponse project = projectAccess.requireMembershipByProjectId(callerId, sprint.getProjectId());
        requireOwnerOrAdmin(callerId, project.key());
        if (sprint.getState() == SprintState.COMPLETED) {
            throw new SprintCompletedException();
        }

        applyUpdate(sprint, request);
        return SprintResponse.from(sprint);
    }

    @Override
    @Transactional
    public SprintResponse start(UUID callerId, UUID sprintId) {
        Sprint sprint = sprintLookupSupport.requireSprint(sprintId);
        ProjectResponse project = projectAccess.requireMembershipByProjectId(callerId, sprint.getProjectId());
        requireOwnerOrAdmin(callerId, project.key());

        if (sprint.getState() != SprintState.PLANNED) {
            throw new IllegalSprintStateTransitionException("Only a planned sprint can be started");
        }
        if (sprint.getStartDate() == null || sprint.getEndDate() == null) {
            throw new SprintDatesRequiredException();
        }
        if (sprintRepository.existsByProjectIdAndState(sprint.getProjectId(), SprintState.ACTIVE)) {
            throw new ActiveSprintAlreadyExistsException();
        }

        int committedPoints = issueService.listForSprint(callerId, project.key(), sprintId).stream()
                .mapToInt(issue -> Objects.requireNonNullElse(issue.storyPoints(), 0))
                .sum();

        sprint.setState(SprintState.ACTIVE);
        sprint.setCommittedPoints(committedPoints);
        eventPublisher.publishEvent(
                new SprintStartedEvent(sprintId, sprint.getProjectId(), project.key(), callerId, sprint.getName()));
        return SprintResponse.from(sprint);
    }

    @Override
    @Transactional
    public SprintResponse complete(UUID callerId, UUID sprintId) {
        Sprint sprint = sprintLookupSupport.requireSprint(sprintId);
        ProjectResponse project = projectAccess.requireMembershipByProjectId(callerId, sprint.getProjectId());
        requireOwnerOrAdmin(callerId, project.key());

        if (sprint.getState() != SprintState.ACTIVE) {
            throw new IllegalSprintStateTransitionException("Only an active sprint can be completed");
        }

        sprint.setState(SprintState.COMPLETED);
        sprint.setCompletedAt(Instant.now());
        eventPublisher.publishEvent(
                new SprintCompletedEvent(sprintId, sprint.getProjectId(), project.key(), callerId,
                        sprint.getName()));
        issueService.moveUnfinishedIssuesToBacklog(callerId, project.key(), sprintId);
        return SprintResponse.from(sprint);
    }

    private void applyUpdate(Sprint sprint, UpdateSprintRequest request) {
        applyName(sprint, request);
        applyGoal(sprint, request);
        applyStartDate(sprint, request);
        applyEndDate(sprint, request);
        validateDateRange(sprint.getStartDate(), sprint.getEndDate());
    }

    private void applyName(Sprint sprint, UpdateSprintRequest request) {
        if (request.name() != null && !request.name().isBlank()) {
            sprint.setName(request.name().trim());
        }
    }

    private void applyGoal(Sprint sprint, UpdateSprintRequest request) {
        if (request.goal() != null) {
            sprint.setGoal(request.goal().trim());
        }
    }

    private void applyStartDate(Sprint sprint, UpdateSprintRequest request) {
        if (request.startDate() != null) {
            sprint.setStartDate(request.startDate());
        }
    }

    private void applyEndDate(Sprint sprint, UpdateSprintRequest request) {
        if (request.endDate() != null) {
            sprint.setEndDate(request.endDate());
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new InvalidSprintDateRangeException();
        }
    }

    private void requireOwnerOrAdmin(UUID callerId, String projectKey) {
        if (!projectAccess.isOwnerOrAdmin(callerId, projectKey)) {
            throw new InsufficientProjectRoleException();
        }
    }
}
