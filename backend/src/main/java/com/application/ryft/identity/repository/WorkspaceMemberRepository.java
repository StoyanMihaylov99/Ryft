package com.application.ryft.identity.repository;

import com.application.ryft.identity.repository.entity.WorkspaceMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    boolean existsByWorkspaceId(UUID workspaceId);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    @Query("select m from WorkspaceMember m join fetch m.user where m.workspace.id = :workspaceId "
            + "order by m.joinedAt asc")
    List<WorkspaceMember> findAllByWorkspaceIdOrderByJoinedAtAsc(@Param("workspaceId") UUID workspaceId);
}
