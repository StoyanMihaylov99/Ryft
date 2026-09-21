/** The live WebSocket push payload for `/topic/projects/{projectKey}/board` — mirrors the backend's
 *  `BoardUpdateMessage` record exactly. Carries only ids and the same small, `eventType`-dependent
 *  `payload` shape `ActivityEventResponse` uses (see `core/activity/models.ts`), never a full
 *  `Issue` — a board/sprint-board reacting to this is expected to reload via its own REST call, not
 *  attempt a field-by-field merge. */
export interface BoardUpdateMessage {
  eventType: string;
  projectId: string;
  projectKey: string;
  issueId: string | null;
  actorId: string;
  timestamp: string;
  payload: Record<string, unknown>;
}
