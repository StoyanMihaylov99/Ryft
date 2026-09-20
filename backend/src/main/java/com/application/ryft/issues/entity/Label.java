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

/** Project-scoped tag an issue can be marked with; {@code name} is unique per project (see the table's constraint). */
@Entity
@Table(name = "labels", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Label {

    @Id
    @UuidGenerator
    private UUID id;

    /** Plain id, not a JPA relation: the projects module's Project entity stays behind its own module. */
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    @Setter
    private String name;

    /** Hex color code, e.g. "#RRGGBB" — format validated at the DTO layer, not re-validated here. */
    @Column(nullable = false)
    @Setter
    private String color;

    public Label(UUID projectId, String name, String color) {
        this.projectId = projectId;
        this.name = name;
        this.color = color;
    }
}
