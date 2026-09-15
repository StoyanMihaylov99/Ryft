package com.application.ryft.workflow.repository;

import com.application.ryft.workflow.entity.WorkflowStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowStatusRepository extends JpaRepository<WorkflowStatus, UUID> {

    @Query("select s from WorkflowStatus s where s.workflowScheme.id = :schemeId order by s.sortOrder asc")
    List<WorkflowStatus> findAllByWorkflowSchemeIdOrderBySortOrderAsc(@Param("schemeId") UUID schemeId);
}
