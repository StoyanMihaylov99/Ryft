package com.application.ryft.workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** Same-module relation to WorkflowScheme — a real JPA relation is fine here, unlike cross-module ids. */
@Entity
@Table(name = "workflow_statuses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowStatus {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_scheme_id", nullable = false)
    private WorkflowScheme workflowScheme;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCategory category;

    /** Board column ordering. "order" is a reserved SQL keyword, hence "sort_order". */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public WorkflowStatus(WorkflowScheme workflowScheme, String name, StatusCategory category, int sortOrder) {
        this.workflowScheme = workflowScheme;
        this.name = name;
        this.category = category;
        this.sortOrder = sortOrder;
    }
}
