export interface Comment {
  id: string;
  issueId: string;
  authorId: string;
  authorDisplayName: string | null;
  authorAvatarUrl: string | null;
  body: string;
  createdAt: string;
  updatedAt: string | null;
}
