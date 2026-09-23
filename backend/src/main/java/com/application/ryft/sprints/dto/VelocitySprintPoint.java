package com.application.ryft.sprints.dto;

import java.time.Instant;
import java.util.UUID;

public record VelocitySprintPoint(
        UUID sprintId,
        String sprintName,
        int committedPoints,
        int completedPoints,
        Instant completedAt
) {
}
