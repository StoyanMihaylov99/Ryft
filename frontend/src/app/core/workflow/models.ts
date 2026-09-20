import { IssueStatus } from '../issue/models';

/** One named status in a project's workflow scheme. `category` is one of the fixed 4 values
 *  (`IssueStatus`) — never itself configurable, only the status's name/order/transitions are. */
export interface WorkflowStatus {
  id: string;
  name: string;
  category: IssueStatus;
  sortOrder: number;
}

/** One legal `fromStatus -> toStatus` move. `fromStatusName`/`toStatusName` are denormalized so the
 *  transition matrix and any error messaging can render without a second status lookup. */
export interface WorkflowTransition {
  id: string;
  fromStatusId: string;
  fromStatusName: string;
  toStatusId: string;
  toStatusName: string;
  name: string | null;
}

export interface WorkflowScheme {
  id: string;
  projectId: string;
  name: string;
  statuses: WorkflowStatus[];
  transitions: WorkflowTransition[];
}

/** One status row within `UpdateWorkflowRequest`. `id: null` creates a new status; an existing
 *  status present in the scheme but missing from the submitted list is deleted (rejected if any
 *  Issue still references it). */
export interface WorkflowStatusEdit {
  id: string | null;
  name: string;
  category: IssueStatus;
  sortOrder: number;
}

/** One transition row within `UpdateWorkflowRequest`. `id: null` creates a new transition; the whole
 *  list is a full replace of the scheme's transition graph. `fromStatusId`/`toStatusId` must both
 *  resolve to a status that already existed in the scheme *before* this request — v1 doesn't support
 *  adding a brand-new status and a transition touching it in the same request. */
export interface WorkflowTransitionEdit {
  id: string | null;
  fromStatusId: string;
  toStatusId: string;
  name: string | null;
}

export interface UpdateWorkflowRequest {
  statuses: WorkflowStatusEdit[];
  transitions: WorkflowTransitionEdit[];
}
