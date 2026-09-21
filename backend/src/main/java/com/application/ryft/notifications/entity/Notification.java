package com.application.ryft.notifications.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @UuidGenerator
    private UUID id;

    /** Plain id, not a JPA relation to identity's User entity — same cross-module pattern as Issue.reporterId. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** columnDefinition avoids Hibernate's auto-generated enum CHECK constraint — see WorkflowStatus.category. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private NotificationType type;

    /** Plain id, not a JPA relation — the issues module's Issue entity stays behind its own module. */
    @Column(name = "issue_id", nullable = false)
    private UUID issueId;

    /**
     * Denormalized copy of the issue's human-readable key (e.g. "TRK-142") at the moment this
     * notification was created — every issue endpoint in this codebase is keyed by that string, never
     * by {@link #issueId}, so without it the frontend would have no way to link a notification to its
     * issue. Unlike {@code actorDisplayName} on {@code NotificationResponse} (resolved at read time,
     * since a display name can change), the key is immutable for an issue's whole lifetime, so storing
     * a copy here carries no staleness risk.
     */
    @Column(name = "issue_key", nullable = false)
    private String issueKey;

    /** Plain id, not a JPA relation to identity's User entity — the user whose action triggered this notification. */
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Notification(UUID userId, NotificationType type, UUID issueId, String issueKey, UUID actorId) {
        this.userId = userId;
        this.type = type;
        this.issueId = issueId;
        this.issueKey = issueKey;
        this.actorId = actorId;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }
}
