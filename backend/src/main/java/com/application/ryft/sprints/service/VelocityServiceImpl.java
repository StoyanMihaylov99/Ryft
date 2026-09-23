package com.application.ryft.sprints.service;

import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.service.IssueService;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.sprints.dto.VelocityResponse;
import com.application.ryft.sprints.dto.VelocitySprintPoint;
import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.entity.SprintState;
import com.application.ryft.sprints.repository.SprintRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VelocityServiceImpl implements VelocityService {

    private final SprintRepository sprintRepository;
    private final SprintsProjectAccess projectAccess;
    private final IssueService issueService;

    public VelocityServiceImpl(SprintRepository sprintRepository, SprintsProjectAccess projectAccess,
            IssueService issueService) {
        this.sprintRepository = sprintRepository;
        this.projectAccess = projectAccess;
        this.issueService = issueService;
    }

    /**
     * Must stay plain {@code @Transactional}, not {@code readOnly = true}, for the same reason as
     * {@link BurndownServiceImpl#getBurndown}: {@link IssueService#listForSprint}, called below for each
     * completed sprint, routes its results through {@code IssueLabelingService.toResponses}, which
     * lazily backfills {@code Issue.workflowStatusId} on any pre-Phase-4 row it resolves — a real write.
     * A read-only transaction would let Hibernate set {@code FlushMode.MANUAL} for the whole call, so
     * that backfill would be staged but never flushed, silently leaving the column null forever.
     */
    @Override
    @Transactional
    public VelocityResponse getVelocity(UUID callerId, String projectKey, int limit) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);

        List<Sprint> mostRecentFirst = sprintRepository.findByProjectIdAndStateOrderByCompletedAtDesc(
                project.id(), SprintState.COMPLETED, PageRequest.of(0, limit));

        List<VelocitySprintPoint> points = mostRecentFirst.stream()
                .map(sprint -> toPoint(callerId, project.key(), sprint))
                .toList()
                .reversed();

        return new VelocityResponse(project.id(), project.key(), points);
    }

    /**
     * Completing a sprint moves every unfinished (non-DONE) issue back to the backlog, so any issue
     * still assigned to a {@code COMPLETED} sprint is, by construction, DONE — completed points is
     * simply the sum of story points across the sprint's current issue set (no workflow status/category
     * inspection needed here, unlike burndown).
     */
    private VelocitySprintPoint toPoint(UUID callerId, String projectKey, Sprint sprint) {
        List<IssueResponse> issues = issueService.listForSprint(callerId, projectKey, sprint.getId());
        int completedPoints = issues.stream()
                .mapToInt(issue -> Objects.requireNonNullElse(issue.storyPoints(), 0))
                .sum();
        int committedPoints = Objects.requireNonNullElse(sprint.getCommittedPoints(), 0);

        return new VelocitySprintPoint(sprint.getId(), sprint.getName(), committedPoints, completedPoints,
                sprint.getCompletedAt());
    }
}
