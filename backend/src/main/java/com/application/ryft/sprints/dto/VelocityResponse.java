package com.application.ryft.sprints.dto;

import java.util.List;
import java.util.UUID;

/** {@code sprints} is ordered chronologically oldest to newest, so a frontend chart reads left-to-right
 * in time order — the same convention {@link BurndownResponse}'s point lists already follow. */
public record VelocityResponse(
        UUID projectId,
        String projectKey,
        List<VelocitySprintPoint> sprints
) {
}
