package com.application.ryft.issues.repository;

import com.application.ryft.issues.entity.IssueComponent;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueComponentRepository extends JpaRepository<IssueComponent, UUID> {

    /** Batch lookup backing {@code IssueLabelingService}'s one-query-per-list-response component resolution. */
    List<IssueComponent> findAllByIssueIdIn(Collection<UUID> issueIds);

    void deleteAllByIssueId(UUID issueId);

    void deleteAllByComponentId(UUID componentId);
}
