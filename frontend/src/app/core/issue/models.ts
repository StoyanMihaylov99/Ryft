export type IssueType = 'STORY' | 'TASK' | 'BUG';
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
}

/** Partial update: an omitted/undefined field is left unchanged server-side. */
export interface UpdateIssueRequest {
  title?: string | null;
  description?: string | null;
  priority?: IssuePriority | null;
  storyPoints?: number | null;
  assigneeId?: string | null;
}
