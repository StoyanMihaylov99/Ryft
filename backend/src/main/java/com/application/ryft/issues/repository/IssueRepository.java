package com.application.ryft.issues.repository;

import com.application.ryft.issues.entity.Issue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueRepository extends JpaRepository<Issue, UUID> {

    Optional<Issue> findByKey(String key);

    List<Issue> findAllByProjectIdOrderByCreatedAtAsc(UUID projectId);

    Optional<Issue> findFirstByProjectIdOrderByBacklogRankDesc(UUID projectId);

    List<Issue> findAllByProjectIdAndSprintIdIsNullOrderByBacklogRankAsc(UUID projectId);

    List<Issue> findAllByProjectIdAndSprintId(UUID projectId, UUID sprintId);

    List<Issue> findAllByProjectIdAndSprintIdOrderByCreatedAtAsc(UUID projectId, UUID sprintId);

    Optional<Issue> findByIdAndProjectId(UUID id, UUID projectId);

    List<Issue> findAllByProjectIdAndParentIssueIdOrderByCreatedAtAsc(UUID projectId, UUID parentIssueId);
}
