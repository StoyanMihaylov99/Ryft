package com.application.ryft.issues.repository;

import com.application.ryft.issues.entity.IssueLabel;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueLabelRepository extends JpaRepository<IssueLabel, UUID> {

    /** Batch lookup backing {@code IssueLabelingService}'s one-query-per-list-response label resolution. */
    List<IssueLabel> findAllByIssueIdIn(Collection<UUID> issueIds);

    void deleteAllByIssueId(UUID issueId);

    void deleteAllByLabelId(UUID labelId);
}
