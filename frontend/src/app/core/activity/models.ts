/**
 * Matches `ActivityEventResponse` exactly. `actorDisplayName` is resolved server-side at read time
 * (via `UserService`, batched across the whole list) — same convention as `Comment`'s
 * `authorDisplayName`/`Notification`'s `actorDisplayName`. `null` when the actor no longer resolves to
 * a user; `activity-display.ts` falls back to a shortened id in that case only.
 * `eventType` is a plain `string`, not a literal union: new event types can appear server-side
 * without a matching frontend release, and `activityText`'s fallback branch handles that case.
 */
export interface ActivityEvent {
  id: string;
  projectId: string;
  issueId: string | null;
  eventType: string;
  actorId: string;
  actorDisplayName: string | null;
  timestamp: string;
  payload: Record<string, unknown>;
}
