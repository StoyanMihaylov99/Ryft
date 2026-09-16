package com.application.ryft.identity.oauth.entity;

import com.application.ryft.identity.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Links a {@link User} to a Google/GitHub identity. A user may have one row per provider. */
@Entity
@Table(name = "oauth_identities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthIdentity {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * {@code columnDefinition} is set explicitly so Hibernate doesn't auto-generate a DB-level CHECK
     * constraint listing the enum's values: {@code ddl-auto: update} (no migration framework here)
     * never widens such a constraint when the enum gains a new constant later, silently breaking every
     * insert of the new value against a table created under the old constant set. See
     * {@code WorkflowStatus.category}'s javadoc, where this was actually hit.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    /** Email reported by the provider at link time. Audit only — not used for matching after creation. */
    @Column
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OAuthIdentity(User user, OAuthProvider provider, String providerUserId, String email) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
    }
}
