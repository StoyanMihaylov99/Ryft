package com.application.ryft.workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * v1 has exactly one scheme per project. {@code projectId} is a
 * plain id, not a JPA relation: the projects module's Project entity stays behind its own module. This
 * is also the direction that keeps the dependency one-way — {@code workflow} points at {@code projects}
 * (via WorkflowProjectAccess, for membership checks), never the reverse, so a project never needs to know a
 * workflow scheme exists. The default scheme is created lazily (see WorkflowServiceImpl) the first time
 * anyone asks for a project's workflow, not eagerly at project-creation time — that's what avoids a
 * circular module dependency here.
 */
@Entity
@Table(name = "workflow_schemes", uniqueConstraints = @UniqueConstraint(columnNames = "project_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowScheme {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Whether this scheme's transitions have ever been deliberately seeded — either by
     * {@code WorkflowServiceImpl}'s any-to-any lazy backfill or by an Owner/Admin's own
     * {@code updateScheme} call — as opposed to simply having zero {@link WorkflowTransition} rows right
     * now. Needed because "zero rows" is ambiguous: it's true both for a scheme that's never been seeded
     * <em>and</em> for one where an admin deliberately submitted {@code transitions: []} to lock the board
     * down, and those two cases must be told apart (see {@code WorkflowServiceImpl.backfillTransitionsIfMissing}).
     * Nullable, not {@code nullable = false}: same lazy-repair-safe pattern as {@code Issue.workflowStatusId}
     * — a column added to an already-populated table under {@code ddl-auto: update} can't be backfilled by
     * a batch migration (no Flyway/Liquibase here), so every pre-existing scheme starts with {@code null}
     * here, and {@code null}/{@code false} are both read as "not yet seeded" until the next read or
     * update sets it {@code true}.
     */
    @Column(name = "transitions_initialized")
    @Setter
    private Boolean transitionsInitialized;

    public WorkflowScheme(UUID projectId, String name) {
        this.projectId = projectId;
        this.name = name;
    }
}
