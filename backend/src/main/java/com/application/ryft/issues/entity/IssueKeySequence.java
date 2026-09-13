package com.application.ryft.issues.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per project, tracking the last issued sequence number for that project's issue keys
 * (e.g. "TRK-142"). {@code IssueKeySequenceRepository.findForUpdate} takes a Postgres row lock before
 * incrementing, which serializes concurrent issue creation within the same project so two issues can
 * never be handed the same key. The very first issue in a project is an exception: there is no row to
 * lock yet, so two concurrent "first" creates could both try to insert the same id — an accepted,
 * narrow race for v1 (results in one request failing with a constraint violation, not a duplicate key).
 */
@Entity
@Table(name = "issue_key_sequences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueKeySequence {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;

    public IssueKeySequence(UUID projectId) {
        this.projectId = projectId;
        this.lastNumber = 0;
    }

    public long incrementAndGet() {
        return ++lastNumber;
    }
}
