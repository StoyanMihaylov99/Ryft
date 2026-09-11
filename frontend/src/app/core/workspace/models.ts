export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export interface WorkspaceMember {
  userId: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  role: WorkspaceRole;
  joinedAt: string;
}

export interface InviteRequest {
  email: string;
  role: WorkspaceRole;
}

export interface CreateWorkspaceRequest {
  name: string;
  slug: string;
}

export interface ChangeRoleRequest {
  role: WorkspaceRole;
}
