import { Issue } from '../issue/models';
import { ProjectRole } from './models';

/**
 * Pure functions over an already-resolved `ProjectRole | null` — no injection, no HTTP. Every page
 * that needs to gate UI on the caller's project role (`board.ts`, `backlog.ts`, `sprints.ts`,
 * `sprint-board.ts`, `issue-detail-panel.ts`) goes through these instead of re-deriving its own
 * `computed(() => myRole() === 'OWNER' || ...)` block, so the actual rules live in exactly one place.
 *
 * `workspace.ts`'s `WorkspaceRole` permission logic is a separate enum/resource (workspace-level, not
 * project-level) and is deliberately not covered here.
 */

/** Owner/Admin only — issue create/update/delete, subtask creation. */
export function canManageIssues(role: ProjectRole | null): boolean {
  return role === 'OWNER' || role === 'ADMIN';
}

/** Owner/Admin only — sprint plan/start/complete, backlog reordering/sprint-membership drags. */
export function canManageSprints(role: ProjectRole | null): boolean {
  return canManageIssues(role);
}

/** Owner/Admin only — labels/components/workflow admin panels, member invites. */
export function canManageProjectSettings(role: ProjectRole | null): boolean {
  return canManageIssues(role);
}

/** Anyone but a Viewer (or a non-member) may change an issue's status — board/sprint-board drag,
 *  the issue detail panel's status dropdown. */
export function canChangeStatus(role: ProjectRole | null): boolean {
  return role !== null && role !== 'VIEWER';
}

/** Anyone but a Viewer (or a non-member) may post/edit/delete a comment. */
export function canComment(role: ProjectRole | null): boolean {
  return canChangeStatus(role);
}

/** Anyone but a Viewer (or a non-member) may share a saved filter project-wide — a private saved
 *  filter is open to any member, including a Viewer. Mirrors
 *  `SavedFilterServiceImpl.requireNotViewer`. */
export function canShareFilter(role: ProjectRole | null): boolean {
  return canChangeStatus(role);
}

export function isOwner(role: ProjectRole | null): boolean {
  return role === 'OWNER';
}

/** Owner/Admin unrestricted; a Member may edit only an issue they're the assignee or reporter of; a
 *  Viewer never. Mirrors `IssueServiceImpl.requireCanEditIssue` exactly — kept here as a pure,
 *  independently-testable function, but `issue-detail-panel.ts` prefers the server-computed
 *  `Issue.callerCanEdit` field over calling this, to avoid the two ever drifting apart (see that
 *  file's `canEditIssue` computed for the full reasoning). */
export function canEditIssue(
  role: ProjectRole | null,
  issue: Pick<Issue, 'assigneeId' | 'reporterId'>,
  currentUserId: string | null,
): boolean {
  if (canManageIssues(role)) {
    return true;
  }
  return (
    role === 'MEMBER' &&
    currentUserId !== null &&
    (issue.assigneeId === currentUserId || issue.reporterId === currentUserId)
  );
}
