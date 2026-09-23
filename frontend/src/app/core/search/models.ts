import { IssueType } from '../issue/models';

/**
 * Body for `POST /projects/{projectKey}/search`. Every field is optional; an omitted, `null`, or
 * empty-array field means "no filter on that field". Values within one field are OR'd together;
 * different fields are AND'd. SUBTASK issues are never returned, regardless of what `types`
 * contains — mirrors every other issue-list endpoint's behavior.
 */
export interface IssueSearchRequest {
  assigneeIds?: string[];
  statusIds?: string[];
  labelIds?: string[];
  componentIds?: string[];
  types?: IssueType[];
  sprintIds?: string[];
  /** Free-text query, AND'd with every other field, matched against title/description via Postgres
   *  full-text search. Blank/omitted means "don't filter on it". Server-capped at 200 characters. */
  text?: string;
}

/**
 * A persisted, named `IssueSearchRequest` — either private to its owner or shared with every other
 * project member. Matches `SavedFilterResponse`.
 */
export interface SavedFilter {
  id: string;
  projectId: string;
  ownerId: string;
  name: string;
  query: IssueSearchRequest;
  isShared: boolean;
  createdAt: string;
}

/** Body for `POST /projects/{projectKey}/filters`. Sharing (`isShared: true`) requires the caller
 *  not be a Viewer — the server returns 403 otherwise. */
export interface CreateSavedFilterRequest {
  name: string;
  query: IssueSearchRequest;
  isShared: boolean;
}
