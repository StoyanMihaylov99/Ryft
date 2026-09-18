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
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** Project-scoped grouping an issue can belong to (e.g. "Backend", "API"); {@code name} is unique per project. */
@Entity
@Table(name = "components", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Component {

    @Id
    @UuidGenerator
    private UUID id;

    /** Plain id, not a JPA relation: the projects module's Project entity stays behind its own module. */
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    @Setter
    private String name;

    public Component(UUID projectId, String name) {
        this.projectId = projectId;
        this.name = name;
    }
}
