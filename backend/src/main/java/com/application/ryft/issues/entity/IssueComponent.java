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

/** Issue-to-Component join row — see {@link IssueLabel}'s javadoc for why this is its own entity. */
@Entity
@Table(name = "issue_components", uniqueConstraints = @UniqueConstraint(columnNames = {"issue_id", "component_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueComponent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "issue_id", nullable = false)
    private UUID issueId;

    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    public IssueComponent(UUID issueId, UUID componentId) {
        this.issueId = issueId;
        this.componentId = componentId;
    }
}
