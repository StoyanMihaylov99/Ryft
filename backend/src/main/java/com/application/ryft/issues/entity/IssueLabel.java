package com.application.ryft.issues.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Issue-to-Label join row. Modeled as its own entity with plain {@code issueId}/{@code labelId}
 * columns and a surrogate {@code id} rather than a {@code @ManyToMany @JoinTable} on {@link Issue} —
 * same reasoning as {@code Issue.parentIssueId} being a plain column instead of a JPA relation: it
 * keeps {@link Issue} free of a lazily-loaded collection (no N+1 risk when {@code IssueResponse} is
 * built, no surprise flush-order issues) and lets {@code IssueServiceImpl}/{@code BoardServiceImpl}
 * batch-resolve labels for many issues with one {@code IN} query via {@code IssueLabelingService}, the
 * same explicit-repository-query style already used for {@code parentIssueId} and {@code sprintId}.
 * The surrogate {@code id} (rather than a composite {@code (issueId, labelId)} primary key) matches
 * every other entity in this module (e.g. {@code Issue.key}, which is also unique but not the PK).
 */
@Entity
@Table(name = "issue_labels", uniqueConstraints = @UniqueConstraint(columnNames = {"issue_id", "label_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueLabel {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "issue_id", nullable = false)
    private UUID issueId;

    @Column(name = "label_id", nullable = false)
    private UUID labelId;

    public IssueLabel(UUID issueId, UUID labelId) {
        this.issueId = issueId;
        this.labelId = labelId;
    }
}
