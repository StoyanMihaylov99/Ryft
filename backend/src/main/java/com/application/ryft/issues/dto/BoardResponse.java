package com.application.ryft.issues.dto;

import java.util.List;
import java.util.UUID;

public record BoardResponse(
        UUID projectId,
        String projectKey,
        List<BoardColumnResponse> columns
) {
}
