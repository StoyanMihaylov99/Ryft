package com.application.ryft.sprints.service;

import com.application.ryft.sprints.dto.CreateSprintRequest;
import com.application.ryft.sprints.dto.SprintResponse;
import com.application.ryft.sprints.dto.UpdateSprintRequest;
import java.util.List;
import java.util.UUID;

public interface SprintService {

    SprintResponse create(UUID callerId, String projectKey, CreateSprintRequest request);

    List<SprintResponse> listForProject(UUID callerId, String projectKey);

    SprintResponse update(UUID callerId, UUID sprintId, UpdateSprintRequest request);

    /**
     * Owner/Admin only. Requires the sprint to be {@code PLANNED} with both dates set, and no other
     * {@code ACTIVE} sprint already running in the same project. Snapshots {@code committedPoints} as
     * the sum of the sprint's issues' story points at the moment it starts.
     */
    SprintResponse start(UUID callerId, UUID sprintId);

    /**
     * Owner/Admin only. Requires the sprint to be {@code ACTIVE}. Sends every unfinished issue in the
     * sprint back to the backlog; {@code DONE} issues keep their {@code sprintId} as sprint history.
     */
    SprintResponse complete(UUID callerId, UUID sprintId);
}
