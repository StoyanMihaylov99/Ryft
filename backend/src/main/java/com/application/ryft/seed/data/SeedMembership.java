package com.application.ryft.seed.data;

import com.application.ryft.projects.entity.ProjectRole;

/**
 * A project membership to add on top of the project creator, who is always its Owner (see
 * {@code ProjectService#create}) and therefore never listed here. {@code ProjectRole} is an enum, not a
 * JPA entity, so importing it directly doesn't cross the "never depend on another module's entity" line
 * — same precedent as {@code issues.dto.BoardColumnResponse} importing {@code workflow.entity.StatusCategory}.
 */
public record SeedMembership(
        String userEmail,
        ProjectRole role
) {
}
