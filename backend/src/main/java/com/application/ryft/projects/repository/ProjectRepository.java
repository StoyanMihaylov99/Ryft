package com.application.ryft.projects.repository;

import com.application.ryft.projects.entity.Project;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByWorkspaceIdAndKey(UUID workspaceId, String key);

    boolean existsByWorkspaceIdAndKey(UUID workspaceId, String key);
}
