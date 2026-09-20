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

    /** columnDefinition avoids Hibernate's auto-generated enum CHECK constraint — see WorkflowStatus.category. */
    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, columnDefinition = "varchar(32)")
    private IssueType type;

    @Column(nullable = false)
    @Setter
    private String title;

    @Column
    @Setter
    private String description;

    /**
     * Legacy Phase-1 status enum — retired from every forward code path (Phase 4 made workflows
     * configurable, see {@link #workflowStatusId}) but kept mapped, still set to {@code TODO} on
     * creation: the underlying {@code status} DB column is {@code NOT NULL} and, since there's no
     * Flyway/Liquibase in this project, {@code ddl-auto: update} can't relax that constraint on an
     * already-populated table (same class of gotcha as DATA_MODEL.md's {@code backlog_rank} note). Its
     * only remaining reader is {@code IssueServiceImpl}'s one-time lazy backfill, which matches this
     * enum constant's name to a {@code WorkflowStatus} category to seed {@link #workflowStatusId} for
     * rows that predate this column.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    @Setter
    private IssueStatus status;

    /**
     * Plain id, not a JPA relation to the workflow module's {@code WorkflowStatus} entity — same
     * cross-module pattern as {@link #projectId}/{@link #sprintId}. Nullable so it can be added to an
     * already-populated table under {@code ddl-auto: update}; rows created before this column existed
     * are repaired by {@code IssueServiceImpl}'s lazy backfill the first time they're read post-deploy,
     * not by a batch migration.
     */
    @Column(name = "workflow_status_id")
    @Setter
    private UUID workflowStatusId;

    /** columnDefinition avoids Hibernate's auto-generated enum CHECK constraint — see WorkflowStatus.category. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    @Setter
    private IssuePriority priority;

    /** Plain id, not a JPA relation to identity's User entity — same pattern as Project.workspaceId. */
    @Column(name = "assignee_id")
    @Setter
    private UUID assigneeId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "story_points")
    @Setter
    private Integer storyPoints;

    /** Plain id, not a JPA relation to the sprints module's Sprint entity — same pattern as projectId. Null means the issue sits in the backlog. */
    @Column(name = "sprint_id")
    @Setter
    private UUID sprintId;

    /** Manual ordering key for the backlog list; see IssueServiceImpl.reorderBacklog for the midpoint-insertion scheme. */
    @Column(name = "backlog_rank", nullable = false)
    @Setter
    private double backlogRank;

    /**
     * Plain id, not a JPA relation (same pattern as sprintId/assigneeId — avoids self-join complexity).
     * Doubles as the epic link (STORY/TASK/BUG -&gt; EPIC) and the subtask parent link
     * (SUBTASK -&gt; STORY/TASK/BUG); which meaning applies is derived from the issue's own type,
     * validated in IssueServiceImpl.
     */
    @Column(name = "parent_issue_id")
    @Setter
    private UUID parentIssueId;

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
            IssuePriority priority, UUID assigneeId, UUID reporterId, double backlogRank) {
        this.projectId = projectId;
        this.key = key;
        this.type = type;
        this.title = title;
        this.description = description;
        this.status = IssueStatus.TODO;
        this.priority = priority;
        this.assigneeId = assigneeId;
        this.reporterId = reporterId;
        this.backlogRank = backlogRank;
    }
}
