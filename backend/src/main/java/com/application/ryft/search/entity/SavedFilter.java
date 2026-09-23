package com.application.ryft.search.entity;

import com.application.ryft.search.dto.IssueSearchRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * A project member's saved, optionally project-shared search — see {@code search.dto.IssueSearchRequest}
 * for the structured criteria this wraps. {@code query} is stored as a native {@code jsonb} column
 * ({@link JdbcTypeCode}({@link SqlTypes#JSON})): Hibernate (auto-)resolves a JSON {@code FormatMapper} at
 * boot from whatever JSON library is on the classpath — this project only has Jackson 3
 * ({@code tools.jackson}), so Hibernate picks its {@code Jackson3JsonFormatMapper} with no extra
 * configuration, and (de)serializes {@link IssueSearchRequest} directly, the same record already used at
 * the {@code search} module's controller boundary. No {@code AttributeConverter}/hand-rolled
 * (de)serialization needed, unlike the pre-Hibernate-6.3 approach.
 *
 * <p>{@code (project_id, owner_id, name)} is unique: two different members may each have their own filter
 * named e.g. "My Bugs" (scoped by {@code owner_id}), but the same member can't save two filters under the
 * same name in the same project — mirrors {@link com.application.ryft.issues.entity.Label}'s
 * {@code (project_id, name)} constraint, narrowed by owner since a filter (unlike a label) is a personal
 * object first, shared only optionally.
 */
@Entity
@Table(name = "saved_filters",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "owner_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedFilter {

    @Id
    @UuidGenerator
    private UUID id;

    /** Plain id, not a JPA relation: the projects module's Project entity stays behind its own module. */
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** Plain id, not a JPA relation to identity's User entity — the creating (and only deleting) member. */
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    @Setter
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Setter
    private IssueSearchRequest query;

    @Column(name = "is_shared", nullable = false)
    @Setter
    private boolean isShared;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SavedFilter(UUID projectId, UUID ownerId, String name, IssueSearchRequest query, boolean isShared) {
        this.projectId = projectId;
        this.ownerId = ownerId;
        this.name = name;
        this.query = query;
        this.isShared = isShared;
    }
}
