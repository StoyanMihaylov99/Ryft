package com.application.ryft.issues.repository;

import com.application.ryft.issues.entity.Component;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComponentRepository extends JpaRepository<Component, UUID> {

    List<Component> findAllByProjectIdOrderByNameAsc(UUID projectId);

    Optional<Component> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByProjectIdAndName(UUID projectId, String name);

    /** Batch-validates a set of ids belong to the given project in one query — see IssueServiceImpl. */
    List<Component> findAllByProjectIdAndIdIn(UUID projectId, Collection<UUID> ids);
}
