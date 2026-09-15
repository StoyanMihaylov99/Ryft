export interface Project {
  id: string;
  workspaceId: string;
  key: string;
  name: string;
  description: string | null;
  createdAt: string;
  archivedAt: string | null;
}

export interface CreateProjectRequest {
  key: string;
  name: string;
  description?: string | null;
}

export type ProjectRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER';

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
