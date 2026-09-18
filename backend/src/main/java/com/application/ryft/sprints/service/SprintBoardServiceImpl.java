package com.application.ryft.sprints.service;

import com.application.ryft.issues.dto.BoardColumnResponse;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.service.IssueService;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.sprints.dto.SprintBoardResponse;
import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.entity.SprintState;
import com.application.ryft.sprints.exception.NoActiveSprintException;
import com.application.ryft.sprints.exception.NotAProjectMemberException;
import com.application.ryft.sprints.exception.ProjectNotFoundException;
import com.application.ryft.sprints.repository.SprintRepository;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import com.application.ryft.workflow.service.WorkflowService;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SprintBoardServiceImpl implements SprintBoardService {

    private final SprintRepository sprintRepository;
    private final SprintsProjectAccess projectAccess;
    private final WorkflowService workflowService;
    private final IssueService issueService;

    public SprintBoardServiceImpl(SprintRepository sprintRepository, SprintsProjectAccess projectAccess,
            WorkflowService workflowService, IssueService issueService) {
        this.sprintRepository = sprintRepository;
        this.projectAccess = projectAccess;
        this.workflowService = workflowService;
        this.issueService = issueService;
    }

    /**
     * Not read-only: on a project's first-ever board view (Kanban or sprint) this transitively
     * triggers {@link WorkflowService#getSchemeForProject} to lazily create the default workflow
     * scheme (a write). Marking this method read-only would join that write into a read-only
     * transaction — Hibernate then sets FlushMode.MANUAL for the whole call, so the inserted
     * scheme/status rows are never flushed before the very next query re-reads them, silently
     * producing an empty board no matter how many times it's requested. There's no exception to see;
     * it just comes back empty. See {@code issues.service.BoardServiceImpl.getBoard}'s javadoc, which
     * documents the original occurrence of this bug and the fix.
     */
    @Override
    @Transactional
    public SprintBoardResponse getBoard(UUID callerId, String projectKey) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        Sprint sprint = sprintRepository.findByProjectIdAndState(project.id(), SprintState.ACTIVE)
                .orElseThrow(NoActiveSprintException::new);
        WorkflowSchemeResponse scheme = requireWorkflowScheme(callerId, projectKey);
        List<IssueResponse> issues = issueService.listForSprint(callerId, projectKey, sprint.getId());

        List<BoardColumnResponse> columns = scheme.statuses().stream()
                .sorted(Comparator.comparingInt(WorkflowStatusResponse::sortOrder))
                .map(status -> new BoardColumnResponse(status.id(), status.name(), status.category(),
                        issuesInCategory(issues, status.category())))
                .toList();

        return new SprintBoardResponse(project.id(), project.key(), sprint.getId(), sprint.getName(), columns);
    }

    /**
     * Translates {@code workflow}'s exception types the same way {@link SprintsProjectAccess}
     * translates {@code projects}' — see {@code issues.service.BoardServiceImpl.requireWorkflowScheme}
     * for the shape this mirrors. Kept as this class's own inline copy rather than reused from
     * {@link SprintsProjectAccess}, which only knows about {@code projects}' exception types.
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

    /**
     * {@code Issue.status} (the issues module's fixed enum) and {@code WorkflowStatus.category} (the
     * workflow module's enum) are deliberately modeled with identical constant names for the fixed
     * Phase 1 scheme — matching by name is what bridges them without either module depending on the
     * other's entity. Mirrors {@code issues.service.BoardServiceImpl.issuesInCategory} exactly.
     */
    private List<IssueResponse> issuesInCategory(List<IssueResponse> issues, StatusCategory category) {
        return issues.stream()
                .filter(issue -> issue.status().name().equals(category.name()))
                .toList();
    }
}
