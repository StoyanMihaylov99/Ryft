package com.application.ryft.sprints.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BurndownResponse(
        UUID sprintId,
        String sprintName,
        LocalDate startDate,
        LocalDate endDate,
        int committedPoints,
        List<BurndownPoint> idealBurndown,
        List<BurndownPoint> actualBurndown
) {
}
