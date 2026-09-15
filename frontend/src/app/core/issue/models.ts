export type IssueType = 'STORY' | 'TASK' | 'BUG';
export type IssueStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';
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
  assigneeId: string | null;
  reporterId: string;
  createdAt: string;
  updatedAt: string | null;
  resolvedAt: string | null;
}

export interface CreateIssueRequest {
  type: IssueType;
  title: string;
  description?: string | null;
  priority?: IssuePriority | null;
  assigneeId?: string | null;
}

/** Partial update: an omitted/undefined field is left unchanged server-side. */
export interface UpdateIssueRequest {
  title?: string | null;
  description?: string | null;
  priority?: IssuePriority | null;
  assigneeId?: string | null;
}
