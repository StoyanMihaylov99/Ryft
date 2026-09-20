import { BoardColumn } from '../board/models';

export type SprintState = 'PLANNED' | 'ACTIVE' | 'COMPLETED';

export interface Sprint {
  id: string;
  projectId: string;
  name: string;
  goal: string | null;
  state: SprintState;
  startDate: string | null;
  endDate: string | null;
  committedPoints: number | null;
  createdAt: string;
  completedAt: string | null;
}

export interface CreateSprintRequest {
  name: string;
  goal?: string | null;
  startDate?: string | null;
  endDate?: string | null;
}

/** Partial update: an omitted/undefined field is left unchanged server-side. */
export interface UpdateSprintRequest {
  name?: string | null;
  goal?: string | null;
  startDate?: string | null;
  endDate?: string | null;
}

/** Same column shape as the Kanban `Board` — the Scrum board just scopes issues to the active sprint. */
export interface SprintBoard {
  projectId: string;
  projectKey: string;
  sprintId: string;
  sprintName: string;
  columns: BoardColumn[];
}

export interface BurndownPoint {
  date: string;
  remainingPoints: number;
}

/**
 * `idealBurndown` always spans the full `startDate`..`endDate` range. `actualBurndown` spans
 * `startDate`..`min(today, endDate)`, so it's shorter than `idealBurndown` for a sprint still in
 * progress and the same length once the sprint has completed.
 */
export interface Burndown {
  sprintId: string;
  sprintName: string;
  startDate: string;
  endDate: string;
  committedPoints: number;
  idealBurndown: BurndownPoint[];
  actualBurndown: BurndownPoint[];
}
