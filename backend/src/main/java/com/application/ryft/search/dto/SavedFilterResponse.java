package com.application.ryft.search.dto;

import com.application.ryft.search.entity.SavedFilter;
import java.time.Instant;
import java.util.UUID;

public record SavedFilterResponse(
        UUID id,
        UUID projectId,
        UUID ownerId,
        String name,
        IssueSearchRequest query,
        boolean isShared,
        Instant createdAt
) {

    public static SavedFilterResponse from(SavedFilter filter) {
        return new SavedFilterResponse(filter.getId(), filter.getProjectId(), filter.getOwnerId(), filter.getName(),
                filter.getQuery(), filter.isShared(), filter.getCreatedAt());
    }
}
