package com.application.ryft.issues.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    @Id
    @UuidGenerator
    private UUID id;

    /** Same-module relation (Issue lives in this module too), unlike the cross-module ids below. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    /** Plain id, not a JPA relation to identity's User entity — same pattern as Issue.reporterId. */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Null until the first edit; {@code @UpdateTimestamp} would stamp this on insert too, breaking "(edited)" detection. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Comment(Issue issue, UUID authorId, String body) {
        this.issue = issue;
        this.authorId = authorId;
        this.body = body;
    }

    public void editBody(String body) {
        this.body = body;
        this.updatedAt = Instant.now();
    }
}
