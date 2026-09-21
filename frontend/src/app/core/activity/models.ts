/**
 * Matches `ActivityEventResponse` exactly. Note it carries `actorId` only, unlike `Comment`'s
 * `authorDisplayName` or `Notification`'s `actorDisplayName` — the backend doesn't denormalize a
 * display name onto this DTO, so `activity-display.ts` can only show the raw id (see that file).
 * `eventType` is a plain `string`, not a literal union: new event types can appear server-side
 * without a matching frontend release, and `activityText`'s fallback branch handles that case.
 */
export interface ActivityEvent {
  id: string;
  projectId: string;
  issueId: string | null;
  eventType: string;
  actorId: string;
  timestamp: string;
  payload: Record<string, unknown>;
}
