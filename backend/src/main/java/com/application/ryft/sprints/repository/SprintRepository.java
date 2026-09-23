package com.application.ryft.sprints.repository;

import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.entity.SprintState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintRepository extends JpaRepository<Sprint, UUID> {

    List<Sprint> findAllByProjectIdOrderByCreatedAtAsc(UUID projectId);

    boolean existsByProjectIdAndState(UUID projectId, SprintState state);

    Optional<Sprint> findByProjectIdAndState(UUID projectId, SprintState state);

    /** Caller passes a {@link Pageable} (e.g. {@code PageRequest.of(0, limit)}) since the trailing-N
     * count is caller-configurable, not fixed — see {@code VelocityServiceImpl}. */
    List<Sprint> findByProjectIdAndStateOrderByCompletedAtDesc(UUID projectId, SprintState state, Pageable pageable);
}
