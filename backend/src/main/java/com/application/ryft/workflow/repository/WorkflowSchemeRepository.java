package com.application.ryft.workflow.repository;

import com.application.ryft.workflow.entity.WorkflowScheme;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowSchemeRepository extends JpaRepository<WorkflowScheme, UUID> {

    Optional<WorkflowScheme> findByProjectId(UUID projectId);
}
