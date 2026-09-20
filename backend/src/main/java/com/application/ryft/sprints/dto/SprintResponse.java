package com.application.ryft.sprints.dto;

import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.entity.SprintState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SprintResponse(
        UUID id,
        UUID projectId,
        String name,
        String goal,
        SprintState state,
        LocalDate startDate,
        LocalDate endDate,
        Integer committedPoints,
        Instant createdAt,
        Instant completedAt
) {

    public static SprintResponse from(Sprint sprint) {
        return new SprintResponse(sprint.getId(), sprint.getProjectId(), sprint.getName(), sprint.getGoal(),
                sprint.getState(), sprint.getStartDate(), sprint.getEndDate(), sprint.getCommittedPoints(),
                sprint.getCreatedAt(), sprint.getCompletedAt());
    }
}
