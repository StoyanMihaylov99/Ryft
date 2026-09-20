package com.application.ryft.issues.service;

import com.application.ryft.issues.dto.BoardColumnResponse;
import com.application.ryft.issues.dto.BoardResponse;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.exception.NotAProjectMemberException;
import com.application.ryft.issues.exception.ProjectNotFoundException;
import com.application.ryft.issues.repository.IssueRepository;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.service.WorkflowService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoardServiceImpl implements BoardService {

    private final IssueRepository issueRepository;
    private final IssueProjectAccess projectAccess;
    private final WorkflowService workflowService;
    private final IssueLabelingService issueLabelingService;

    public BoardServiceImpl(IssueRepository issueRepository, IssueProjectAccess projectAccess,
            WorkflowService workflowService, IssueLabelingService issueLabelingService) {
        this.issueRepository = issueRepository;
        this.projectAccess = projectAccess;
        this.workflowService = workflowService;
        this.issueLabelingService = issueLabelingService;
    }

    /**
     * Not read-only: on a project's first board view this transitively triggers
     * {@link WorkflowService#getSchemeForProject} to lazily create the default workflow scheme (a
     * write), and {@code IssueLabelingService.toResponses} may itself lazily backfill
     * {@code Issue.workflowStatusId} on any pre-Phase-4 row it resolves. Marking this method read-only
     * would join those writes into a read-only transaction — Hibernate then sets FlushMode.MANUAL for
     * the whole call, so the inserted/updated rows are never flushed before the very next query re-reads
     * them, silently producing an empty board no matter how many times it's requested. There's no
     * exception to see; it just comes back empty.
     */
    @Override
    @Transactional
    public BoardResponse getBoard(UUID callerId, String projectKey) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        WorkflowSchemeResponse scheme = requireWorkflowScheme(callerId, projectKey);
        ProjectRole role = projectAccess.getRole(callerId, projectKey);
        List<Issue> issues = issueRepository.findAllByProjectIdAndTypeNotOrderByCreatedAtAsc(project.id(),
                IssueType.SUBTASK);
        List<IssueResponse> issueResponses = issueLabelingService.toResponses(issues, scheme, callerId, role);
        Map<UUID, List<IssueResponse>> issuesByStatusId = issueResponses.stream()
                .collect(Collectors.groupingBy(IssueResponse::statusId));

        List<BoardColumnResponse> columns = scheme.statuses().stream()
                .sorted(Comparator.comparingInt(WorkflowStatusResponse::sortOrder))
                .map(status -> new BoardColumnResponse(status.id(), status.name(), status.category(),
                        issuesByStatusId.getOrDefault(status.id(), List.of())))
                .toList();

        return new BoardResponse(project.id(), project.key(), columns);
    }

    /**
     * Translates {@code workflow}'s exception types the same way {@link IssueProjectAccess} translates
     * {@code projects}' — defensive: this call can't actually diverge from the membership check just
     * above (same caller, same project, same instant), but every cross-module call is typed
     * consistently regardless, per this project's standing convention (see IssueProjectAccess's javadoc).
     */
    private WorkflowSchemeResponse requireWorkflowScheme(UUID callerId, String projectKey) {
        try {
            return workflowService.getSchemeForProject(callerId, projectKey);
        } catch (com.application.ryft.workflow.exception.ProjectNotFoundException e) {
            throw new ProjectNotFoundException(projectKey);
        } catch (com.application.ryft.workflow.exception.NotAProjectMemberException e) {
            throw new NotAProjectMemberException();
        }
    }
}
