package com.application.ryft.issues.service;

import com.application.ryft.issues.dto.ChangeIssueStatusRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.UpdateIssueRequest;
import java.util.List;
import java.util.UUID;

public interface IssueService {

    IssueResponse create(UUID callerId, String projectKey, CreateIssueRequest request);

    List<IssueResponse> listForProject(UUID callerId, String projectKey);

    /** Sprint-scoped variant of {@link #listForProject(UUID, String)}, additive — used by callers that
     * want only one sprint's issues (e.g. a later scrum board/burndown step). */
    List<IssueResponse> listForProject(UUID callerId, String projectKey, UUID sprintId);

    /** Issues with no sprint assigned, ordered by their manual backlog rank. */
    List<IssueResponse> listBacklogForProject(UUID callerId, String projectKey);

    /** All issues in a given sprint, regardless of status. */
    List<IssueResponse> listForSprint(UUID callerId, String projectKey, UUID sprintId);

    IssueResponse get(UUID callerId, String issueKey);

    IssueResponse update(UUID callerId, String issueKey, UpdateIssueRequest request);

    IssueResponse changeStatus(UUID callerId, String issueKey, ChangeIssueStatusRequest request);

    /** {@code sprintId == null} moves the issue back to the backlog. Owner/Admin only. */
    IssueResponse moveToSprint(UUID callerId, String issueKey, UUID sprintId);

    /** Owner/Admin only. */
    IssueResponse reorderBacklog(UUID callerId, String issueKey, String beforeIssueKey, String afterIssueKey);

    /**
     * Clears {@code sprintId} on every non-DONE issue in the given sprint, sending them back to the
     * backlog. No internal Owner/Admin gate: the only caller ({@code sprints.SprintServiceImpl.complete},
     * a later step) already checks it before calling in; membership is still checked here for
     * consistency with every other method on this service.
     */
    void moveUnfinishedIssuesToBacklog(UUID callerId, String projectKey, UUID sprintId);

    void delete(UUID callerId, String issueKey);
}
