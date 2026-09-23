package com.application.ryft.issues.repository;

import com.application.ryft.issues.dto.IssueSearchCriteria;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssueComponent;
import com.application.ryft.issues.entity.IssueLabel;
import com.application.ryft.issues.entity.IssueType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.jpa.domain.Specification;

/**
 * Predicate builders backing {@code IssueServiceImpl#search} — the AND-combining structured filter
 * (assignee/status/label/type/sprint/free-text) that {@code ProjectIssuesController}'s GET precedence
 * chain deferred to this phase (see that controller's javadoc). Kept as its own small holder class, mirroring
 * how {@code IssueLabelingService}/{@code IssueWorkflowAccess} are already split out of
 * {@code IssueServiceImpl} as focused collaborators, rather than growing that class with raw
 * {@code CriteriaBuilder} plumbing.
 *
 * <p>{@code labelIds}/{@code componentIds} are matched via an {@code id IN (subquery)} against
 * {@link IssueLabel}/{@link IssueComponent} — the {@code Specification} equivalent of
 * {@code IssueRepository}'s existing {@code findAllByProjectIdAndLabelIdAndTypeNotOrderByCreatedAtAsc}
 * JPQL sub-select, consistent with those being modeled as their own join entities rather than a
 * {@code @ManyToMany} on {@link Issue} (see {@link IssueLabel}'s javadoc).
 *
 * <p>{@code text} (free-text search) is folded in the same way, except its id list is resolved by
 * {@code IssueRepository#searchIssueIdsByText} *before* this method runs, not by a subquery here — a
 * Postgres full-text match isn't expressible as a Criteria API predicate, so {@code IssueRepository#search}
 * pre-resolves it and hands the plain id list to {@link #matching} as {@code textMatchingIssueIds}.
 */
final class IssueSpecifications {

    private IssueSpecifications() {
    }

    /**
     * Every predicate {@code IssueServiceImpl#search} needs, ANDed together — {@code projectId} and
     * excluding SUBTASK always apply. {@code textMatchingIssueIds} is {@code null} when {@code criteria.text()}
     * is blank (no text filter applied), or the — possibly empty — list of ids {@code searchIssueIdsByText}
     * matched; an empty (non-null) list must still narrow the result to nothing, so it's handled explicitly
     * as an always-false predicate rather than relying on a bare {@code cb.in(List.of())}, whose behavior
     * isn't guaranteed consistent across JPA providers.
     */
    static Specification<Issue> matching(UUID projectId, IssueSearchCriteria criteria,
            List<UUID> textMatchingIssueIds) {
        List<Specification<Issue>> specs = new ArrayList<>();
        specs.add(projectId(projectId));
        specs.add(typeNot(IssueType.SUBTASK));
        addIfPresent(specs, criteria.types(), IssueSpecifications::typeIn);
        addIfPresent(specs, criteria.statusIds(), IssueSpecifications::statusIn);
        addIfPresent(specs, criteria.assigneeIds(), IssueSpecifications::assigneeIn);
        addIfPresent(specs, criteria.sprintIds(), IssueSpecifications::sprintIn);
        addIfPresent(specs, criteria.labelIds(), IssueSpecifications::hasLabel);
        addIfPresent(specs, criteria.componentIds(), IssueSpecifications::hasComponent);
        if (textMatchingIssueIds != null) {
            specs.add(idIn(textMatchingIssueIds));
        }
        return Specification.allOf(specs);
    }

    private static <V> void addIfPresent(List<Specification<Issue>> specs, List<V> values,
            Function<List<V>, Specification<Issue>> factory) {
        if (values != null && !values.isEmpty()) {
            specs.add(factory.apply(values));
        }
    }

    private static Specification<Issue> projectId(UUID projectId) {
        return (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
    }

    private static Specification<Issue> typeNot(IssueType excludedType) {
        return (root, query, cb) -> cb.notEqual(root.get("type"), excludedType);
    }

    private static Specification<Issue> typeIn(List<IssueType> types) {
        return (root, query, cb) -> root.<IssueType>get("type").in(types);
    }

    private static Specification<Issue> statusIn(List<UUID> statusIds) {
        return (root, query, cb) -> root.<UUID>get("workflowStatusId").in(statusIds);
    }

    private static Specification<Issue> assigneeIn(List<UUID> assigneeIds) {
        return (root, query, cb) -> root.<UUID>get("assigneeId").in(assigneeIds);
    }

    private static Specification<Issue> sprintIn(List<UUID> sprintIds) {
        return (root, query, cb) -> root.<UUID>get("sprintId").in(sprintIds);
    }

    private static Specification<Issue> hasLabel(List<UUID> labelIds) {
        return (root, query, cb) -> issueIdIn(root, query, IssueLabel.class, "labelId", labelIds);
    }

    private static Specification<Issue> hasComponent(List<UUID> componentIds) {
        return (root, query, cb) -> issueIdIn(root, query, IssueComponent.class, "componentId", componentIds);
    }

    /** An empty (but non-null) {@code ids} must match nothing — see {@link #matching}'s javadoc. */
    private static Specification<Issue> idIn(List<UUID> ids) {
        return (root, query, cb) -> ids.isEmpty() ? cb.disjunction() : root.<UUID>get("id").in(ids);
    }

    private static <J> jakarta.persistence.criteria.Predicate issueIdIn(Root<Issue> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query, Class<J> joinEntity, String joinColumnAttribute,
            List<UUID> ids) {
        Subquery<UUID> subquery = query.subquery(UUID.class);
        Root<J> joinRoot = subquery.from(joinEntity);
        Path<UUID> issueId = joinRoot.get("issueId");
        subquery.select(issueId).where(joinRoot.<UUID>get(joinColumnAttribute).in(ids));
        Path<UUID> id = root.get("id");
        return id.in(subquery);
    }
}
