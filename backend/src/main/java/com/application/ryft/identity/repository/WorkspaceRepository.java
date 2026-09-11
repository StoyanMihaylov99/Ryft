package com.application.ryft.identity.repository;

import com.application.ryft.identity.repository.entity.Workspace;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    Optional<Workspace> findFirstByOrderByCreatedAtAsc();
}
