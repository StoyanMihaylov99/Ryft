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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * v1 has exactly one scheme per project. {@code projectId} is a
 * plain id, not a JPA relation: the projects module's Project entity stays behind its own module. This
 * is also the direction that keeps the dependency one-way — {@code workflow} points at {@code projects}
 * (via ProjectAccess, for membership checks), never the reverse, so a project never needs to know a
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

    public WorkflowScheme(UUID projectId, String name) {
        this.projectId = projectId;
        this.name = name;
    }
}
