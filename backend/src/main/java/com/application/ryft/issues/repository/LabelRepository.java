package com.application.ryft.issues.repository;

import com.application.ryft.issues.entity.Label;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabelRepository extends JpaRepository<Label, UUID> {

    List<Label> findAllByProjectIdOrderByNameAsc(UUID projectId);

    Optional<Label> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByProjectIdAndName(UUID projectId, String name);

    /** Batch-validates a set of ids belong to the given project in one query — see IssueServiceImpl. */
    List<Label> findAllByProjectIdAndIdIn(UUID projectId, Collection<UUID> ids);
}
