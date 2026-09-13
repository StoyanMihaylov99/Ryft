package com.application.ryft.issues.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "issues", uniqueConstraints = @UniqueConstraint(columnNames = "key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Issue {

    @Id
    @UuidGenerator
    private UUID id;

    /** Plain id, not a JPA relation: the projects module's Project entity stays behind its own module. */
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** e.g. "TRK-142" — {@code projectKey + "-" + sequence number}, generated once at creation. */
    @Column(name = "key", nullable = false)
    private String key;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false)
    private IssueType type;

    @Column(nullable = false)
    @Setter
    private String title;

    @Column
    @Setter
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private IssueStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private IssuePriority priority;

    /** Plain id, not a JPA relation to identity's User entity — same pattern as Project.workspaceId. */
    @Column(name = "assignee_id")
    @Setter
    private UUID assigneeId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "resolved_at")
    @Setter
    private Instant resolvedAt;

    public Issue(UUID projectId, String key, IssueType type, String title, String description,
            IssuePriority priority, UUID assigneeId, UUID reporterId) {
        this.projectId = projectId;
        this.key = key;
        this.type = type;
        this.title = title;
        this.description = description;
        this.status = IssueStatus.TODO;
        this.priority = priority;
        this.assigneeId = assigneeId;
        this.reporterId = reporterId;
    }
}
