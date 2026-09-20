package com.application.ryft.issues.service;

import com.application.ryft.issues.dto.ComponentResponse;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.LabelResponse;
import com.application.ryft.issues.entity.Component;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssueComponent;
import com.application.ryft.issues.entity.IssueLabel;
import com.application.ryft.issues.entity.Label;
import com.application.ryft.issues.exception.InvalidComponentReferenceException;
import com.application.ryft.issues.exception.InvalidLabelReferenceException;
import com.application.ryft.issues.repository.ComponentRepository;
import com.application.ryft.issues.repository.IssueComponentRepository;
import com.application.ryft.issues.repository.IssueLabelRepository;
import com.application.ryft.issues.repository.LabelRepository;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Everything about how {@link Issue}s carry {@link Label}s/{@link Component}s and their workflow status:
 * resolving both into {@link IssueResponse} (batched across a whole list response — one {@code IN}
 * query per response, same pattern as {@code CommentServiceImpl.fetchAuthors} — rather than per issue,
 * which would be N+1 on every project issue list, backlog, sprint, or board request) and validating/
 * persisting the {@code labelIds}/{@code componentIds} on create/update. Shared by {@link IssueServiceImpl}
 * and {@link BoardServiceImpl} so both build the same response shape the same way.
 *
 * <p>Labels/components are modeled as their own join-row entities ({@link IssueLabel}/
 * {@link IssueComponent}) with plain {@code issueId}/{@code labelId} columns rather than a
 * {@code @ManyToMany @JoinTable} on {@link Issue} — see {@link IssueLabel}'s javadoc for why.
 *
 * <p>{@code toResponses} also backfills {@code Issue.workflowStatusId} for any row that predates that
 * column (matching the legacy {@code IssueStatus} enum's name to a same-category {@code WorkflowStatus}
 * — the same enum-name bridge {@code issues.service.BoardServiceImpl} used before Phase 4) and computes
 * {@code callerCanEdit} per issue from the caller's project role plus assignee/reporter involvement. Since
 * the backfill is a real write, every caller of this class that can reach a null
 * {@code workflowStatusId} must not be {@code @Transactional(readOnly = true)} — see ARCHITECTURE.md's
 * "readOnly + transitive lazy write" note.
 */
@org.springframework.stereotype.Component
class IssueLabelingService {

    private final IssueLabelRepository issueLabelRepository;
    private final LabelRepository labelRepository;
    private final IssueComponentRepository issueComponentRepository;
    private final ComponentRepository componentRepository;
    private final IssueWorkflowAccess workflowAccess;

    IssueLabelingService(IssueLabelRepository issueLabelRepository, LabelRepository labelRepository,
            IssueComponentRepository issueComponentRepository, ComponentRepository componentRepository,
            IssueWorkflowAccess workflowAccess) {
        this.issueLabelRepository = issueLabelRepository;
        this.labelRepository = labelRepository;
        this.issueComponentRepository = issueComponentRepository;
        this.componentRepository = componentRepository;
        this.workflowAccess = workflowAccess;
    }

    IssueResponse toResponse(Issue issue, UUID callerId, String projectKey, ProjectRole callerRole) {
        return toResponses(List.of(issue), callerId, projectKey, callerRole).get(0);
    }

    /** Variant for callers that already fetched the scheme (e.g. {@code IssueServiceImpl.create}), avoiding a duplicate call. */
    IssueResponse toResponse(Issue issue, WorkflowSchemeResponse scheme, UUID callerId, ProjectRole callerRole) {
        return toResponses(List.of(issue), scheme, callerId, callerRole).get(0);
    }

    List<IssueResponse> toResponses(List<Issue> issues, UUID callerId, String projectKey, ProjectRole callerRole) {
        if (issues.isEmpty()) {
            return List.of();
        }
        WorkflowSchemeResponse scheme = workflowAccess.requireScheme(callerId, projectKey);
        return toResponses(issues, scheme, callerId, callerRole);
    }

    /** Variant for callers (e.g. {@code BoardServiceImpl}) that already fetched the scheme for their own purposes, avoiding a duplicate call. */
    List<IssueResponse> toResponses(List<Issue> issues, WorkflowSchemeResponse scheme, UUID callerId, ProjectRole callerRole) {
        if (issues.isEmpty()) {
            return List.of();
        }
        issues.forEach(issue -> backfillWorkflowStatusIfMissing(issue, scheme));
        Map<UUID, WorkflowStatusResponse> statusesById = scheme.statuses().stream()
                .collect(Collectors.toMap(WorkflowStatusResponse::id, status -> status));

        List<UUID> issueIds = issues.stream().map(Issue::getId).toList();
        Map<UUID, List<LabelResponse>> labelsByIssueId = labelsByIssueId(issueIds);
        Map<UUID, List<ComponentResponse>> componentsByIssueId = componentsByIssueId(issueIds);
        return issues.stream()
                .map(issue -> IssueResponse.from(issue, statusesById.get(issue.getWorkflowStatusId()),
                        labelsByIssueId.getOrDefault(issue.getId(), List.of()),
                        componentsByIssueId.getOrDefault(issue.getId(), List.of()),
                        callerCanEdit(callerRole, callerId, issue)))
                .toList();
    }

    /** Owner/Admin always; Member only when they're the issue's assignee or reporter; Viewer never. */
    private boolean callerCanEdit(ProjectRole callerRole, UUID callerId, Issue issue) {
        if (callerRole == ProjectRole.OWNER || callerRole == ProjectRole.ADMIN) {
            return true;
        }
        return callerRole == ProjectRole.MEMBER
                && (callerId.equals(issue.getAssigneeId()) || callerId.equals(issue.getReporterId()));
    }

    /**
     * Backfills {@code Issue.workflowStatusId} in place for a legacy row that predates that column,
     * matching its old fixed {@code IssueStatus} enum name to a same-category {@code WorkflowStatus} in
     * the scheme. Package-private (not just used internally by {@code toResponses}): also called
     * directly by {@code IssueServiceImpl.changeStatus}/{@code moveUnfinishedIssuesToBacklog}, which need
     * a resolved id to compare against *before* building a response.
     */
    void backfillWorkflowStatusIfMissing(Issue issue, WorkflowSchemeResponse scheme) {
        if (issue.getWorkflowStatusId() == null) {
            issue.setWorkflowStatusId(resolveStatusIdForLegacyEnum(issue, scheme));
        }
    }

    private UUID resolveStatusIdForLegacyEnum(Issue issue, WorkflowSchemeResponse scheme) {
        return scheme.statuses().stream()
                .filter(status -> status.category().name().equals(issue.getStatus().name()))
                .map(WorkflowStatusResponse::id)
                .findFirst()
                .orElseGet(() -> scheme.statuses().get(0).id());
    }

    /**
     * Validates {@code labelIds}/{@code componentIds} resolve within the project — called before the
     * new {@link Issue} row is inserted, so an invalid id fails fast without an insert that would only
     * be rolled back (same "validate before persisting" order as {@code IssueServiceImpl}'s parent-link
     * checks). {@code null} is treated the same as an empty list, since a brand-new issue has no
     * existing labels/components to preserve — see {@code CreateIssueRequest}'s javadoc.
     */
    void validateOnCreate(UUID projectId, List<UUID> labelIds, List<UUID> componentIds) {
        requireLabelsInProject(labelIds == null ? List.of() : labelIds, projectId);
        requireComponentsInProject(componentIds == null ? List.of() : componentIds, projectId);
    }

    /** Persists the join rows for an already-saved, already-{@link #validateOnCreate}d issue. */
    void attachOnCreate(Issue issue, UUID projectId, List<UUID> labelIds, List<UUID> componentIds) {
        replaceLabels(issue, projectId, labelIds == null ? List.of() : labelIds);
        replaceComponents(issue, projectId, componentIds == null ? List.of() : componentIds);
    }

    /** {@code null} means "leave the existing labels unchanged"; a non-null list (including empty) replaces them — see UpdateIssueRequest. */
    void applyLabels(Issue issue, UUID projectId, List<UUID> labelIds) {
        if (labelIds != null) {
            replaceLabels(issue, projectId, labelIds);
        }
    }

    /** See {@link #applyLabels} — same null-vs-empty convention. */
    void applyComponents(Issue issue, UUID projectId, List<UUID> componentIds) {
        if (componentIds != null) {
            replaceComponents(issue, projectId, componentIds);
        }
    }

    private void replaceLabels(Issue issue, UUID projectId, List<UUID> labelIds) {
        List<Label> labels = requireLabelsInProject(labelIds, projectId);
        issueLabelRepository.deleteAllByIssueId(issue.getId());
        issueLabelRepository.saveAll(labels.stream()
                .map(label -> new IssueLabel(issue.getId(), label.getId()))
                .toList());
    }

    private void replaceComponents(Issue issue, UUID projectId, List<UUID> componentIds) {
        List<Component> components = requireComponentsInProject(componentIds, projectId);
        issueComponentRepository.deleteAllByIssueId(issue.getId());
        issueComponentRepository.saveAll(components.stream()
                .map(component -> new IssueComponent(issue.getId(), component.getId()))
                .toList());
    }

    private List<Label> requireLabelsInProject(List<UUID> labelIds, UUID projectId) {
        if (labelIds.isEmpty()) {
            return List.of();
        }
        List<Label> found = labelRepository.findAllByProjectIdAndIdIn(projectId, labelIds);
        if (found.size() != Set.copyOf(labelIds).size()) {
            throw new InvalidLabelReferenceException(firstMissing(labelIds, found.stream().map(Label::getId)));
        }
        return found;
    }

    private List<Component> requireComponentsInProject(List<UUID> componentIds, UUID projectId) {
        if (componentIds.isEmpty()) {
            return List.of();
        }
        List<Component> found = componentRepository.findAllByProjectIdAndIdIn(projectId, componentIds);
        if (found.size() != Set.copyOf(componentIds).size()) {
            throw new InvalidComponentReferenceException(
                    firstMissing(componentIds, found.stream().map(Component::getId)));
        }
        return found;
    }

    private UUID firstMissing(List<UUID> requestedIds, java.util.stream.Stream<UUID> foundIds) {
        Set<UUID> found = foundIds.collect(Collectors.toSet());
        return requestedIds.stream().filter(id -> !found.contains(id)).findFirst().orElseThrow();
    }

    private Map<UUID, List<LabelResponse>> labelsByIssueId(List<UUID> issueIds) {
        List<IssueLabel> issueLabels = issueLabelRepository.findAllByIssueIdIn(issueIds);
        Set<UUID> labelIds = issueLabels.stream().map(IssueLabel::getLabelId).collect(Collectors.toSet());
        Map<UUID, LabelResponse> labelsById = labelRepository.findAllById(labelIds).stream()
                .collect(Collectors.toMap(Label::getId, LabelResponse::from));
        return issueLabels.stream()
                .filter(issueLabel -> labelsById.containsKey(issueLabel.getLabelId()))
                .collect(Collectors.groupingBy(IssueLabel::getIssueId,
                        Collectors.collectingAndThen(
                                Collectors.mapping(issueLabel -> labelsById.get(issueLabel.getLabelId()),
                                        Collectors.toList()),
                                labels -> labels.stream().sorted(Comparator.comparing(LabelResponse::name)).toList())));
    }

    private Map<UUID, List<ComponentResponse>> componentsByIssueId(List<UUID> issueIds) {
        List<IssueComponent> issueComponents = issueComponentRepository.findAllByIssueIdIn(issueIds);
        Set<UUID> componentIds = issueComponents.stream().map(IssueComponent::getComponentId)
                .collect(Collectors.toSet());
        Map<UUID, ComponentResponse> componentsById = componentRepository.findAllById(componentIds).stream()
                .collect(Collectors.toMap(Component::getId, ComponentResponse::from));
        return issueComponents.stream()
                .filter(issueComponent -> componentsById.containsKey(issueComponent.getComponentId()))
                .collect(Collectors.groupingBy(IssueComponent::getIssueId,
                        Collectors.collectingAndThen(
                                Collectors.mapping(issueComponent -> componentsById.get(issueComponent.getComponentId()),
                                        Collectors.toList()),
                                components -> components.stream()
                                        .sorted(Comparator.comparing(ComponentResponse::name))
                                        .toList())));
    }
}
