package com.application.ryft.issues.repository;

import com.application.ryft.issues.dto.IssueSearchCriteria;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssueType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link JpaSpecificationExecutor} backs {@code IssueServiceImpl#search} — the one dynamic,
 * AND-combining query on this repository (see {@code IssueSpecifications}); every other method here
 * stays a plain derived/JPQL query, matching the rest of this repository's style.
 */
public interface IssueRepository extends JpaRepository<Issue, UUID>, JpaSpecificationExecutor<Issue> {

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

    /**
     * "Done" (or any other category) is a set of status ids under a configurable scheme, not one fixed
     * status — see {@code WorkflowService.getStatusIdsInCategory}, whose result feeds this query's
     * {@code IN} clause.
     */
    long countByProjectIdAndParentIssueIdAndWorkflowStatusIdIn(UUID projectId, UUID parentIssueId,
            Collection<UUID> workflowStatusIds);

    /** Backs {@code WorkflowServiceImpl}'s status-deletion guard — see {@code IssueService.existsAnyWithWorkflowStatusId}. */
    boolean existsByProjectIdAndWorkflowStatusId(UUID projectId, UUID workflowStatusId);

    /**
     * Backs {@code GET /projects/{projectKey}/issues?labelId=} — a JPQL sub-select against
     * {@code IssueLabel} rather than a {@code @ManyToMany} traversal, consistent with {@link IssueLabel}
     * being modeled as its own entity (see its javadoc).
     */
    @Query("""
            select i from Issue i where i.projectId = :projectId and i.type <> :excludedType
            and i.id in (select il.issueId from IssueLabel il where il.labelId = :labelId)
            order by i.createdAt asc
            """)
    List<Issue> findAllByProjectIdAndLabelIdAndTypeNotOrderByCreatedAtAsc(@Param("projectId") UUID projectId,
            @Param("labelId") UUID labelId, @Param("excludedType") IssueType excludedType);

    /** Component-scoped variant of {@link #findAllByProjectIdAndLabelIdAndTypeNotOrderByCreatedAtAsc}. */
    @Query("""
            select i from Issue i where i.projectId = :projectId and i.type <> :excludedType
            and i.id in (select ic.issueId from IssueComponent ic where ic.componentId = :componentId)
            order by i.createdAt asc
            """)
    List<Issue> findAllByProjectIdAndComponentIdAndTypeNotOrderByCreatedAtAsc(@Param("projectId") UUID projectId,
            @Param("componentId") UUID componentId, @Param("excludedType") IssueType excludedType);

    /**
     * Backs {@code IssueServiceImpl#search} — the AND-combining structured filter (assignee/status/label/
     * type/sprint/free-text) that {@code ProjectIssuesController}'s GET precedence chain deferred to this
     * phase. Delegates the actual predicate-building to {@link IssueSpecifications}, kept package-private
     * here rather than exposed on {@code IssueServiceImpl}, so the {@code Specification}/{@code CriteriaBuilder}
     * plumbing stays fully inside this repository package.
     *
     * <p>{@code criteria.text()} can't be expressed as a {@code Specification} predicate directly — Postgres
     * full-text matching needs {@code to_tsvector}/{@code plainto_tsquery}, not portable via JPQL/Criteria
     * — so a blank/non-blank {@code text} resolves the matching issue ids via {@link #searchIssueIdsByText}
     * first, and that pre-resolved list is folded in as one more {@code id IN (...)} predicate alongside
     * {@code labelIds}/{@code componentIds}'s existing subquery predicates.
     */
    default List<Issue> search(UUID projectId, IssueSearchCriteria criteria, Sort sort) {
        List<UUID> textMatchingIssueIds = criteria.text() != null && !criteria.text().isBlank()
                ? searchIssueIdsByText(projectId, criteria.text())
                : null;
        return findAll(IssueSpecifications.matching(projectId, criteria, textMatchingIssueIds), sort);
    }

    /**
     * Postgres full-text search against {@code title}/{@code description}, scoped to one project — a
     * native query since {@code to_tsvector}/{@code plainto_tsquery} aren't expressible via portable
     * JPQL/Criteria. {@code plainto_tsquery} (not {@code to_tsquery}) so arbitrary user input — including
     * tsquery operator characters like {@code &}/{@code |}/{@code !} — is parsed as a plain phrase instead
     * of tsquery syntax the caller would otherwise need to know and escape, avoiding a Postgres syntax
     * error on that input. {@code :text} is a bind parameter, never concatenated, so this is safe from SQL
     * injection the same way every other parameterized query in this project is.
     *
     * <p>The {@code tsvector} is computed at query time rather than stored in a generated column: this
     * project has no Flyway/Liquibase (see {@code Issue.status}'s javadoc), and {@code ddl-auto: update}
     * can't express a Postgres {@code GENERATED ALWAYS AS (...) STORED} column or a GIN index from plain
     * entity annotations. Acceptable at this project's portfolio scale — no index means a full scan of one
     * project's issues per search, not a concern until issue counts are far larger than this app targets.
     */
    @Query(value = """
            select id from issues
            where project_id = :projectId
            and to_tsvector('english', coalesce(title, '') || ' ' || coalesce(description, ''))
                @@ plainto_tsquery('english', :text)
            """, nativeQuery = true)
    List<UUID> searchIssueIdsByText(@Param("projectId") UUID projectId, @Param("text") String text);
}
