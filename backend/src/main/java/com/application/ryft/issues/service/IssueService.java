package com.application.ryft.issues.service;

import com.application.ryft.issues.dto.ChangeIssueStatusRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.CreateSubtaskRequest;
import com.application.ryft.issues.dto.EpicProgressResponse;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.UpdateIssueRequest;
import java.util.List;
import java.util.UUID;

public interface IssueService {

    IssueResponse create(UUID callerId, String projectKey, CreateIssueRequest request);

    /** Every list/backlog/sprint method below excludes SUBTASK issues — see {@link #listSubtasks}. */
    List<IssueResponse> listForProject(UUID callerId, String projectKey);

    /** Sprint-scoped variant of {@link #listForProject(UUID, String)}, additive — used by callers that
     * want only one sprint's issues (e.g. a later scrum board/burndown step). */
    List<IssueResponse> listForProject(UUID callerId, String projectKey, UUID sprintId);

    /**
     * Epic-scoped variant of {@link #listForProject(UUID, String)} — filters to issues whose
     * {@code parentIssueId} equals the given Epic's id. A distinct method name rather than another
     * {@code (UUID, String, UUID)} overload, since Java can't overload on parameter name alone; wired
     * the same way as the {@code sprintId} filter otherwise (controller -&gt; service -&gt; repository).
     */
    List<IssueResponse> listForProjectByEpic(UUID callerId, String projectKey, UUID epicId);

    /**
     * Label-scoped variant of {@link #listForProject(UUID, String)} — filters to issues with the given
     * Label attached. Wired the same way as the {@code sprintId}/{@code epicId} filters (see
     * {@code ProjectIssuesController} for the precedence rule when several filters are passed together).
     */
    List<IssueResponse> listForProjectByLabel(UUID callerId, String projectKey, UUID labelId);

    /** Component-scoped variant of {@link #listForProject(UUID, String)} — filters to issues with the given Component attached. */
    List<IssueResponse> listForProjectByComponent(UUID callerId, String projectKey, UUID componentId);

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

    /**
     * Deleting an EPIC nulls out {@code parentId} on every issue that was linked to it rather than
     * deleting them (an Epic disappearing shouldn't take its Stories/Tasks/Bugs with it). Deleting any
     * other issue cascade-deletes its own SUBTASKs (and their comments) first — a Subtask has no
     * independent value once its parent is gone.
     */
    void delete(UUID callerId, String issueKey);

    /**
     * Creates a SUBTASK whose {@code parentId} is the given issue — Owner/Admin only, same gate as
     * {@link #create}. The parent must be a STORY, TASK, or BUG (not an EPIC, not itself a SUBTASK).
     */
    IssueResponse createSubtask(UUID callerId, String issueKey, CreateSubtaskRequest request);

    /** Subtasks of the given issue, ordered by creation time — any project member. */
    List<IssueResponse> listSubtasks(UUID callerId, String issueKey);

    /**
     * Done/total count of the given Epic's directly-linked STORY/TASK/BUG issues — any project member.
     * Deliberately 2-level only: a linked issue's own Subtasks are not rolled up (see
     * {@link com.application.ryft.issues.dto.EpicProgressResponse}). Throws
     * {@link com.application.ryft.issues.exception.NotAnEpicException} if {@code epicKey} doesn't
     * resolve to an EPIC.
     */
    EpicProgressResponse getEpicProgress(UUID callerId, String epicKey);

    /**
     * Whether any issue in the given project still references the given workflow status — backs
     * {@code WorkflowServiceImpl}'s status-deletion guard (a {@code WorkflowStatus} can't be deleted
     * while an {@code Issue} points at it). Deliberately takes raw ids, not a project key/caller: this is
     * an internal cross-module check called by {@code workflow}, not a caller-facing endpoint, so it
     * skips the usual membership gate — the caller-facing gate already happened on
     * {@code WorkflowServiceImpl.updateScheme}'s own entry point.
     */
    boolean existsAnyWithWorkflowStatusId(UUID projectId, UUID workflowStatusId);
}
