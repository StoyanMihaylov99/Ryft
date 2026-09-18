package com.application.ryft.issues.repository;

import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.entity.IssueType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueRepository extends JpaRepository<Issue, UUID> {

    Optional<Issue> findByKey(String key);

    /**
     * {@code excludedType} is always {@link IssueType#SUBTASK} at every call site: subtasks are
     * checklist-style children only visible nested under their parent (see
     * {@code IssueServiceImpl#listSubtasks}), never in the project's top-level issue list.
     */
    List<Issue> findAllByProjectIdAndTypeNotOrderByCreatedAtAsc(UUID projectId, IssueType excludedType);

    Optional<Issue> findFirstByProjectIdOrderByBacklogRankDesc(UUID projectId);

    /** See {@link #findAllByProjectIdAndTypeNotOrderByCreatedAtAsc} — same subtask-exclusion rationale, backlog view. */
    List<Issue> findAllByProjectIdAndSprintIdIsNullAndTypeNotOrderByBacklogRankAsc(UUID projectId, IssueType excludedType);

    /** Unfiltered by type: only used internally by {@code moveUnfinishedIssuesToBacklog}, not a list endpoint. */
    List<Issue> findAllByProjectIdAndSprintId(UUID projectId, UUID sprintId);

    /** See {@link #findAllByProjectIdAndTypeNotOrderByCreatedAtAsc} — same subtask-exclusion rationale, sprint view. */
    List<Issue> findAllByProjectIdAndSprintIdAndTypeNotOrderByCreatedAtAsc(UUID projectId, UUID sprintId, IssueType excludedType);

    Optional<Issue> findByIdAndProjectId(UUID id, UUID projectId);

    List<Issue> findAllByProjectIdAndParentIssueIdOrderByCreatedAtAsc(UUID projectId, UUID parentIssueId);

    /** Every child of a given parent regardless of project (a subtask always shares its parent's project). */
    List<Issue> findAllByParentIssueIdOrderByCreatedAtAsc(UUID parentIssueId);

    /**
     * Backs an Epic's progress bar (see {@code IssueServiceImpl#getEpicProgress}): counts only the
     * Epic's directly-linked STORY/TASK/BUG issues, not their own Subtasks — a count query rather than
     * loading full {@link Issue} rows just to size a list.
     */
    long countByProjectIdAndParentIssueId(UUID projectId, UUID parentIssueId);

    long countByProjectIdAndParentIssueIdAndStatus(UUID projectId, UUID parentIssueId, IssueStatus status);
}
