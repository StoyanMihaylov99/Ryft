package com.application.ryft.issues.dto;

import com.application.ryft.workflow.entity.StatusCategory;
import java.util.List;
import java.util.UUID;

public record BoardColumnResponse(
        UUID statusId,
        String name,
        StatusCategory category,
        List<IssueResponse> issues
) {
}
