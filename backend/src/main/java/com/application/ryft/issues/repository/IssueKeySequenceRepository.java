package com.application.ryft.issues.repository;

import com.application.ryft.issues.entity.IssueKeySequence;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueKeySequenceRepository extends JpaRepository<IssueKeySequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from IssueKeySequence s where s.projectId = :projectId")
    Optional<IssueKeySequence> findForUpdate(@Param("projectId") UUID projectId);
}
