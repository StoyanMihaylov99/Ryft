package com.application.ryft.sprints.dto;

import java.util.List;
import java.util.UUID;

public record SprintBoardResponse(
        UUID projectId,
        String projectKey,
        UUID sprintId,
        String sprintName,
        List<com.application.ryft.issues.dto.BoardColumnResponse> columns
) {
}
