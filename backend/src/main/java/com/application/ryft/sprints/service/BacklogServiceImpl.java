package com.application.ryft.sprints.service;

import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.service.IssueService;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.entity.SprintState;
import com.application.ryft.sprints.exception.CannotMoveIssueIntoCompletedSprintException;
import com.application.ryft.sprints.exception.InsufficientProjectRoleException;
import com.application.ryft.sprints.exception.IssueNotFoundException;
import com.application.ryft.sprints.exception.IssueSprintProjectMismatchException;
import com.application.ryft.sprints.exception.NotAProjectMemberException;
import com.application.ryft.sprints.exception.ProjectNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BacklogServiceImpl implements BacklogService {

    private final IssueService issueService;
    private final SprintsProjectAccess projectAccess;
    private final SprintLookupSupport sprintLookupSupport;

    public BacklogServiceImpl(IssueService issueService, SprintsProjectAccess projectAccess,
            SprintLookupSupport sprintLookupSupport) {
        this.issueService = issueService;
        this.projectAccess = projectAccess;
        this.sprintLookupSupport = sprintLookupSupport;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> listBacklog(UUID callerId, String projectKey) {
        try {
            return issueService.listBacklogForProject(callerId, projectKey);
        } catch (com.application.ryft.issues.exception.ProjectNotFoundException e) {
            throw new ProjectNotFoundException(projectKey);
        } catch (com.application.ryft.issues.exception.NotAProjectMemberException e) {
            throw new NotAProjectMemberException();
        }
    }

    @Override
    @Transactional
    public IssueResponse moveIssue(UUID callerId, String issueKey, UUID sprintId) {
        IssueResponse issue = requireIssue(callerId, issueKey);
        ProjectResponse project = projectAccess.requireMembershipByProjectId(callerId, issue.projectId());
        if (!projectAccess.isOwnerOrAdmin(callerId, project.key())) {
            throw new InsufficientProjectRoleException();
        }

        if (sprintId != null) {
            Sprint sprint = sprintLookupSupport.requireSprint(sprintId);
            if (!sprint.getProjectId().equals(issue.projectId())) {
                throw new IssueSprintProjectMismatchException();
            }
            if (sprint.getState() == SprintState.COMPLETED) {
                throw new CannotMoveIssueIntoCompletedSprintException();
            }
        }

        return moveToSprint(callerId, issueKey, sprintId);
    }

    /**
     * Translates {@code issues}' exception types the same way {@link SprintsProjectAccess} translates
     * {@code projects}' — see {@code issues.service.BoardServiceImpl.requireWorkflowScheme} for the
     * shape this mirrors. The {@code ProjectNotFoundException} branch is defensive only: an issue
     * that {@link IssueService#get} just returned always has a real, existing project behind it.
     */
    private IssueResponse requireIssue(UUID callerId, String issueKey) {
        try {
            return issueService.get(callerId, issueKey);
        } catch (com.application.ryft.issues.exception.IssueNotFoundException e) {
            throw new IssueNotFoundException(issueKey);
        } catch (com.application.ryft.issues.exception.NotAProjectMemberException e) {
            throw new NotAProjectMemberException();
        } catch (com.application.ryft.issues.exception.ProjectNotFoundException e) {
            throw new ProjectNotFoundException(issueKey);
        }
    }

    /**
     * Translates {@code issues}' exception types the same way {@link #requireIssue} does. Needed
     * because {@link IssueService#moveToSprint} redundantly re-checks issue existence, membership and
     * role via its own {@code issues.service.IssueProjectAccess}-backed path, which can observe
     * different state than the checks earlier in {@link #moveIssue} under concurrent access (e.g. the
     * issue is deleted, or the caller's role changes, between the two checks).
     */
    private IssueResponse moveToSprint(UUID callerId, String issueKey, UUID sprintId) {
        try {
            return issueService.moveToSprint(callerId, issueKey, sprintId);
        } catch (com.application.ryft.issues.exception.IssueNotFoundException e) {
            throw new IssueNotFoundException(issueKey);
        } catch (com.application.ryft.issues.exception.NotAProjectMemberException e) {
            throw new NotAProjectMemberException();
        } catch (com.application.ryft.issues.exception.ProjectNotFoundException e) {
            throw new ProjectNotFoundException(issueKey);
        } catch (com.application.ryft.issues.exception.InsufficientProjectRoleException e) {
            throw new InsufficientProjectRoleException();
        }
    }
}
