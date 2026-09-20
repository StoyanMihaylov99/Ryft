export type IssueType = 'STORY' | 'TASK' | 'BUG' | 'EPIC' | 'SUBTASK';
export type IssueStatus = 'TODO' | 'BLOCKED' | 'IN_PROGRESS' | 'DONE';
export type IssuePriority = 'LOWEST' | 'LOW' | 'MEDIUM' | 'HIGH' | 'HIGHEST';

export interface Label {
  id: string;
  projectId: string;
  name: string;
  /** A 6-digit hex code, e.g. `#4287F5` — user-chosen and arbitrary, so anything rendering this as
   *  a chip background must compute its own legible text color rather than assuming white/black
   *  (see `contrastTextColor` in `shared/color-contrast.ts`). */
  color: string;
}

/**
 * Named `ProjectComponent`, not `Component` (which is the backend entity's name): a `Component`
 * interface here would collide with `@angular/core`'s `Component` decorator, imported by every
 * Angular component file in this app.
 */
export interface ProjectComponent {
  id: string;
  projectId: string;
  name: string;
}

export interface Issue {
  id: string;
  projectId: string;
  key: string;
  type: IssueType;
  title: string;
  description: string | null;
  /** `statusId` is the source of truth (a real `WorkflowStatus` row in the project's configurable
   *  scheme); `statusName`/`statusCategory` are denormalized alongside it so a card/column can
   *  render a label or group by category without a second lookup. `statusCategory` is the fixed
   *  4-value category union (`IssueStatus`) — unaffected by workflow configuration, unlike
   *  `statusId`/`statusName`. */
  statusId: string;
  statusName: string;
  statusCategory: IssueStatus;
  priority: IssuePriority;
  storyPoints: number | null;
  assigneeId: string | null;
  reporterId: string;
  sprintId: string | null;
  /** A generic parent link whose meaning depends on `type`: the linked Epic's id for a
   *  STORY/TASK/BUG (or null if unlinked), the parent issue's id for a SUBTASK (never null), or
   *  always null for an EPIC itself. */
  parentId: string | null;
  labels: Label[];
  components: ProjectComponent[];
  createdAt: string;
  updatedAt: string | null;
  resolvedAt: string | null;
  /** Owner/Admin, or Member-and-involved (assignee or reporter) — the one permission that can't be
   *  derived from `ProjectResponse.callerRole` alone. UX-only, like `callerRole`: the actual trust
   *  boundary is server-side enforcement on the write endpoints. */
  callerCanEdit: boolean;
}

export interface CreateIssueRequest {
  type: IssueType;
  title: string;
  description?: string | null;
  priority?: IssuePriority | null;
  storyPoints?: number | null;
  assigneeId?: string | null;
  /** Only valid for a STORY/TASK/BUG, and only when it points at an EPIC in the same project.
   *  Creating a SUBTASK this way also needs a parentId (pointing at a STORY/TASK/BUG), but
   *  `createSubtask`/`CreateSubtaskRequest` is the simpler path — it resolves the parent from the
   *  URL instead. */
  parentId?: string | null;
  /** Omitted and empty both mean "no labels/components" — there's no existing state to preserve on
   *  create, unlike `UpdateIssueRequest` below. */
  labelIds?: string[];
  componentIds?: string[];
}

/** Partial update: an omitted/undefined field is left unchanged server-side. */
export interface UpdateIssueRequest {
  title?: string | null;
  description?: string | null;
  priority?: IssuePriority | null;
  storyPoints?: number | null;
  assigneeId?: string | null;
  /** Same STORY/TASK/BUG-to-EPIC rules as on create (a SUBTASK's parentId instead must resolve to
   *  a STORY/TASK/BUG). There is no way to clear an existing link through this endpoint (null
   *  means "don't touch it") — point it at a different parent instead. */
  parentId?: string | null;
  /**
   * The one departure from "omitted means don't touch": since these are multi-select fields,
   * `undefined`/omitted still means "leave the existing set unchanged", but an explicit empty
   * array (`[]`) means "clear all labels/components" — a real, distinguishable request, unlike a
   * scalar field such as `assigneeId` where JSON has no way to say "empty" separately from
   * "absent". Only send `[]` once the user has actually touched the selection; sending it
   * unconditionally would silently wipe out an untouched issue's existing labels/components.
   */
  labelIds?: string[];
  componentIds?: string[];
}

/** Slimmer than `CreateIssueRequest`: `type` (always SUBTASK) and `parentId` are both implied by the
 *  `POST /issues/{issueKey}/subtasks` URL. */
export interface CreateSubtaskRequest {
  title: string;
  description?: string | null;
  priority?: IssuePriority | null;
  assigneeId?: string | null;
}

/** `totalCount`/`doneCount` cover only the Epic's directly-linked STORY/TASK/BUG issues — a linked
 *  issue's own Subtasks are a level further down and are not rolled up into this count (mirrors
 *  EpicProgressResponse's javadoc on the backend). `percentDone` is `0` when `totalCount` is `0`. */
export interface EpicProgress {
  totalCount: number;
  doneCount: number;
  percentDone: number;
}

/** `color` must be a 6-digit hex code (e.g. `#4287F5`); name is unique per project (case-sensitive). */
export interface CreateLabelRequest {
  name: string;
  color: string;
}

/** Partial update: an omitted/undefined field is left unchanged. There's no way to clear either
 *  field — a label always needs a name and a color. */
export interface UpdateLabelRequest {
  name?: string;
  color?: string;
}

/** name is unique per project (case-sensitive). */
export interface CreateComponentRequest {
  name: string;
}

/** Partial update: an omitted/undefined name is left unchanged. */
export interface UpdateComponentRequest {
  name?: string;
}
