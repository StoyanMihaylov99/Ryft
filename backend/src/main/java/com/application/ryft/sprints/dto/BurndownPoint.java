package com.application.ryft.sprints.dto;

import java.time.LocalDate;

public record BurndownPoint(LocalDate date, int remainingPoints) {
}
