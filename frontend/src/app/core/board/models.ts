import { Issue, IssueStatus } from '../issue/models';

export interface BoardColumn {
  statusId: string;
  name: string;
  category: IssueStatus;
  issues: Issue[];
}

export interface Board {
  projectId: string;
  projectKey: string;
  columns: BoardColumn[];
}
