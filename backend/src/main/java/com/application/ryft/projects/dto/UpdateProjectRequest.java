package com.application.ryft.projects.dto;

import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @Size(max = 100) String name,
        @Size(max = 2000) String description
) {
}
