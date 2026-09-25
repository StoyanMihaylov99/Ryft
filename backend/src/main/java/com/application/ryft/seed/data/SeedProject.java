package com.application.ryft.seed.data;

import java.util.List;

/**
 * One project's full declarative shape — everything {@code DemoProjectInstaller} needs to build it
 * through {@code projects}/{@code issues}/{@code workflow}/{@code sprints}/{@code search}'s public
 * services. The project's creator (always the demo user, see {@code DemoDataInstaller}) becomes its
 * Owner automatically; {@code members} adds everyone else.
 */
public record SeedProject(
        String key,
        String name,
        String description,
        List<SeedMembership> members,
        List<SeedLabel> labels,
        List<SeedComponent> components,
        List<SeedSprint> sprints,
        List<SeedIssue> issues,
        List<SeedSavedFilter> savedFilters
) {
}
