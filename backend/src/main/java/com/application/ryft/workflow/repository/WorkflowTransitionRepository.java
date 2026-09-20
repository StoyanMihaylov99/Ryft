package com.application.ryft.workflow.repository;

import com.application.ryft.workflow.entity.WorkflowTransition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransition, UUID> {

    @Query("select t from WorkflowTransition t where t.workflowScheme.id = :schemeId")
    List<WorkflowTransition> findAllByWorkflowSchemeId(@Param("schemeId") UUID schemeId);

    @Query("""
            select count(t) > 0 from WorkflowTransition t
            where t.workflowScheme.id = :schemeId and t.fromStatus.id = :fromStatusId and t.toStatus.id = :toStatusId
            """)
    boolean existsByWorkflowSchemeIdAndFromStatusIdAndToStatusId(@Param("schemeId") UUID schemeId,
            @Param("fromStatusId") UUID fromStatusId, @Param("toStatusId") UUID toStatusId);

    /** Deletes every transition into or out of a status about to be deleted, avoiding an FK violation. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from WorkflowTransition t where t.fromStatus.id = :statusId or t.toStatus.id = :statusId")
    void deleteAllReferencingStatus(@Param("statusId") UUID statusId);
}
