package com.application.ryft.sprints.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateSprintRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String goal,
        LocalDate startDate,
        LocalDate endDate
) {
}
