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
import lombok.Setter;
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

    /**
     * {@code columnDefinition} is set explicitly so Hibernate doesn't auto-generate a DB-level CHECK
     * constraint listing the enum's values at table-creation time: {@code ddl-auto: update} (this
     * project has no migration framework) only ever adds missing tables/columns, it never widens an
     * existing CHECK constraint — so adding {@code StatusCategory.BLOCKED} later would otherwise leave
     * every already-created database rejecting the new value with a 500, even though the same fresh
     * schema created after the enum change would work fine. Hit exactly this while adding BLOCKED.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private StatusCategory category;

    /** Board column ordering. "order" is a reserved SQL keyword, hence "sort_order". */
    @Column(name = "sort_order", nullable = false)
    @Setter
    private int sortOrder;

    public WorkflowStatus(WorkflowScheme workflowScheme, String name, StatusCategory category, int sortOrder) {
        this.workflowScheme = workflowScheme;
        this.name = name;
        this.category = category;
        this.sortOrder = sortOrder;
    }
}
