package com.application.ryft.projects.dto;

import com.application.ryft.projects.entity.ProjectRole;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code callerRole} lets the frontend gate every flat Owner/Admin-only action from data it already
 * fetches, instead of re-deriving permissions from a separate members call. UX-only — backend
 * enforcement (each service's own role check) remains the actual trust boundary regardless of what this
 * field says.
 */
public record ProjectResponse(
        UUID id,
        UUID workspaceId,
        String key,
        String name,
        String description,
        Instant createdAt,
        Instant archivedAt,
        ProjectRole callerRole
) {

    /**
     * Pre-{@code callerRole} shape, kept for call sites (mostly test fixtures unrelated to
     * permission behavior) that don't care about the caller's role — defaults it to {@code null}.
     */
    public ProjectResponse(UUID id, UUID workspaceId, String key, String name, String description, Instant createdAt,
            Instant archivedAt) {
        this(id, workspaceId, key, name, description, createdAt, archivedAt, null);
    }
}
