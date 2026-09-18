export type IssueType = 'STORY' | 'TASK' | 'BUG' | 'EPIC';
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
  /** The linked Epic's id for a STORY/TASK/BUG, or null if unlinked. Always null for an EPIC itself. */
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
  /** Only valid for a STORY/TASK/BUG, and only when it points at an EPIC in the same project. */
  parentId?: string | null;
}

/** Partial update: an omitted/undefined field is left unchanged server-side. */
export interface UpdateIssueRequest {
  title?: string | null;
  description?: string | null;
  priority?: IssuePriority | null;
  storyPoints?: number | null;
  assigneeId?: string | null;
  /** Same STORY/TASK/BUG-to-EPIC rules as on create. There is no way to clear an existing link
   *  through this endpoint (null means "don't touch it") — point it at a different EPIC instead. */
  parentId?: string | null;
}
