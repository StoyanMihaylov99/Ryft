package com.application.ryft.search.repository;

import com.application.ryft.search.entity.SavedFilter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedFilterRepository extends JpaRepository<SavedFilter, UUID> {

    /**
     * Every filter visible to {@code callerId} in {@code projectId}: their own (shared or not), plus
     * every other member's shared one — a single query rather than two lists merged in the service, since
     * the "own OR others'-shared" condition is naturally one {@code WHERE} clause.
     */
    @Query("""
            select sf from SavedFilter sf where sf.projectId = :projectId
            and (sf.ownerId = :ownerId or sf.isShared = true)
            order by sf.name asc
            """)
    List<SavedFilter> findAllVisibleToCaller(@Param("projectId") UUID projectId, @Param("ownerId") UUID ownerId);

    /** Scoped to the project so a filter from a different project 404s instead of leaking its existence. */
    Optional<SavedFilter> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByProjectIdAndOwnerIdAndName(UUID projectId, UUID ownerId, String name);

    /**
     * Owner-scoped delete — defense in depth alongside {@code SavedFilterServiceImpl}'s own ownership
     * check, so a bug upstream can't turn into deleting another member's filter at the repository layer.
     */
    long deleteByIdAndProjectIdAndOwnerId(UUID id, UUID projectId, UUID ownerId);
}
