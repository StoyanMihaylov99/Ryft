export type IssueType = 'STORY' | 'TASK' | 'BUG' | 'EPIC' | 'SUBTASK';
export type IssueStatus = 'TODO' | 'BLOCKED' | 'IN_PROGRESS' | 'DONE';
export type IssuePriority = 'LOWEST' | 'LOW' | 'MEDIUM' | 'HIGH' | 'HIGHEST';

export interface Issue {
  id: string;
  projectId: string;
  key: string;
  type: IssueType;
  title: string;
  description: string | null;
  status: IssueStatus;
  priority: IssuePriority;
  storyPoints: number | null;
  assigneeId: string | null;
  reporterId: string;
  sprintId: string | null;
  /** A generic parent link whose meaning depends on `type`: the linked Epic's id for a
   *  STORY/TASK/BUG (or null if unlinked), the parent issue's id for a SUBTASK (never null), or
   *  always null for an EPIC itself. */
  parentId: string | null;
  createdAt: string;
  updatedAt: string | null;
  resolvedAt: string | null;
}

export interface CreateIssueRequest {
  type: IssueType;
  title: string;
  description?: string | null;
  priority?: IssuePriority | null;
  storyPoints?: number | null;
  assigneeId?: string | null;
  /** Only valid for a STORY/TASK/BUG, and only when it points at an EPIC in the same project.
   *  Creating a SUBTASK this way also needs a parentId (pointing at a STORY/TASK/BUG), but
   *  `createSubtask`/`CreateSubtaskRequest` is the simpler path — it resolves the parent from the
   *  URL instead. */
  parentId?: string | null;
}

/** Partial update: an omitted/undefined field is left unchanged server-side. */
export interface UpdateIssueRequest {
  title?: string | null;
  description?: string | null;
  priority?: IssuePriority | null;
  storyPoints?: number | null;
  assigneeId?: string | null;
  /** Same STORY/TASK/BUG-to-EPIC rules as on create (a SUBTASK's parentId instead must resolve to
   *  a STORY/TASK/BUG). There is no way to clear an existing link through this endpoint (null
   *  means "don't touch it") — point it at a different parent instead. */
  parentId?: string | null;
}

/** Slimmer than `CreateIssueRequest`: `type` (always SUBTASK) and `parentId` are both implied by the
 *  `POST /issues/{issueKey}/subtasks` URL. */
export interface CreateSubtaskRequest {
  title: string;
  description?: string | null;
  priority?: IssuePriority | null;
  assigneeId?: string | null;
}
