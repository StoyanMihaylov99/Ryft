export type ProjectRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER';

/** `callerRole` lets a page gate every flat Owner/Admin-only action from data it already fetches,
 *  instead of loading the full member list and filtering it down to the caller's own row. */
export interface Project {
  id: string;
  workspaceId: string;
  key: string;
  name: string;
  description: string | null;
  createdAt: string;
  archivedAt: string | null;
  callerRole: ProjectRole | null;
}

export interface CreateProjectRequest {
  key: string;
  name: string;
  description?: string | null;
}

export interface ProjectMember {
  userId: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  role: ProjectRole;
  addedAt: string;
}

export interface AddProjectMemberRequest {
  email: string;
  role: ProjectRole;
}
