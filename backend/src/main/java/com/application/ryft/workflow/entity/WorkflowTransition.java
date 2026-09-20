package com.application.ryft.workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Defines one legal {@code fromStatus -> toStatus} move within a {@link WorkflowScheme}. Same-module
 * relations to {@link WorkflowScheme}/{@link WorkflowStatus} — real JPA {@code @ManyToOne}s are fine
 * here, unlike the plain-UUID cross-module pattern used for {@code Issue.workflowStatusId}. Uniqueness
 * on {@code (workflowScheme, fromStatus, toStatus)} is enforced at the service layer (see
 * {@code WorkflowServiceImpl}), not via a DB constraint — same convention as {@code Label.name}'s
 * service-level duplicate check.
 */
@Entity
@Table(name = "workflow_transitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowTransition {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_scheme_id", nullable = false)
    private WorkflowScheme workflowScheme;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_status_id", nullable = false)
    private WorkflowStatus fromStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_status_id", nullable = false)
    private WorkflowStatus toStatus;

    @Column(nullable = false)
    @Setter
    private String name;

    public WorkflowTransition(WorkflowScheme workflowScheme, WorkflowStatus fromStatus, WorkflowStatus toStatus,
            String name) {
        this.workflowScheme = workflowScheme;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.name = name;
    }
}
