package com.application.ryft.sprints.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "sprints")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sprint {

    @Id
    @UuidGenerator
    private UUID id;

    /** Plain id, not a JPA relation: the projects module's Project entity stays behind its own module. */
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    @Setter
    private String name;

    @Column
    @Setter
    private String goal;

    /** columnDefinition avoids Hibernate's auto-generated enum CHECK constraint — see WorkflowStatus.category. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    @Setter
    private SprintState state;

    @Column(name = "start_date")
    @Setter
    private LocalDate startDate;

    @Column(name = "end_date")
    @Setter
    private LocalDate endDate;

    /** Snapshot of summed story points across the sprint's issues, taken when the sprint starts. */
    @Column(name = "committed_points")
    @Setter
    private Integer committedPoints;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    @Setter
    private Instant completedAt;

    public Sprint(UUID projectId, String name, String goal, LocalDate startDate, LocalDate endDate) {
        this.projectId = projectId;
        this.name = name;
        this.goal = goal;
        this.state = SprintState.PLANNED;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
