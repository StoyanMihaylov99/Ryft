package com.application.ryft.projects.entity;

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

@Entity
@Table(name = "projects", uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @UuidGenerator
    private UUID id;

    /** Plain id, not a JPA relation: the identity module's Workspace entity stays behind its own module. */
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "key", nullable = false)
    private String key;

    @Column(nullable = false)
    @Setter
    private String name;

    @Column
    @Setter
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "archived_at")
    @Setter
    private Instant archivedAt;

    public Project(UUID workspaceId, String key, String name, String description) {
        this.workspaceId = workspaceId;
        this.key = key;
        this.name = name;
        this.description = description;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }
}
