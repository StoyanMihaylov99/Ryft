package com.application.ryft.sprints.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Partial update: a null field is left unchanged, same convention as {@code issues.dto.UpdateIssueRequest}. */
public record UpdateSprintRequest(
        @Size(max = 200) String name,
        String goal,
        LocalDate startDate,
        LocalDate endDate
) {
}
