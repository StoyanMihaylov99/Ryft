package com.application.ryft.issues.repository;

import com.application.ryft.issues.entity.IssueLabel;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueLabelRepository extends JpaRepository<IssueLabel, UUID> {

    /** Batch lookup backing {@code IssueLabelingService}'s one-query-per-list-response label resolution. */
    List<IssueLabel> findAllByIssueIdIn(Collection<UUID> issueIds);

    /**
     * A bulk JPQL delete, executed immediately — not a derived {@code deleteAllBy...}, which loads the
     * rows and queues one {@code remove} each. Hibernate flushes queued INSERTs before DELETEs, so
     * {@code IssueLabelingService}'s delete-then-reinsert replace would otherwise insert a kept
     * (issue, label) pair while its old row still exists and violate the unique constraint.
     */
    @Modifying
    @Query("delete from IssueLabel j where j.issueId = :issueId")
    void deleteAllByIssueId(@Param("issueId") UUID issueId);

    void deleteAllByLabelId(UUID labelId);
}
