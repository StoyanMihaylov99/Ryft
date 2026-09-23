import { ActivityEvent } from './models';

/**
 * Pure display logic for an `ActivityEvent`, kept independently testable — mirrors
 * `core/project/permissions.ts`'s convention. `actorDisplayName` (see that model's doc) is preferred;
 * a shortened id is only ever shown as a fallback for an actor who no longer resolves to a user.
 */

function actorLabel(event: ActivityEvent): string {
  return event.actorDisplayName ?? `User ${event.actorId.slice(0, 8)}`;
}

function payloadString(payload: Record<string, unknown>, key: string): string {
  const value = payload[key];
  return typeof value === 'string' ? value : '';
}

/** Turns a `snake_case`/`dot.case` event type into a readable fallback, e.g. `"issue.foo_bar"` ->
 *  `"issue foo bar"` — used only for an `eventType` this function doesn't otherwise recognize. */
function humanizeEventType(eventType: string): string {
  return eventType.replace(/[._]/g, ' ');
}

export function activityText(event: ActivityEvent): string {
  const actor = actorLabel(event);
  const payload = event.payload;

  switch (event.eventType) {
    case 'issue.created':
      return `${actor} created this issue as a ${payloadString(payload, 'issue_type')}`;
    case 'issue.status_changed':
      return `${actor} changed the status from ${payloadString(payload, 'from_status')} to ${payloadString(payload, 'to_status')}`;
    case 'issue.assignee_changed':
      return payload['previous_assignee_id'] === null
        ? `${actor} assigned this issue`
        : `${actor} reassigned this issue`;
    case 'comment.added':
      return `${actor} commented: "${payloadString(payload, 'excerpt')}"`;
    case 'comment.updated':
      return `${actor} edited a comment: "${payloadString(payload, 'excerpt')}"`;
    case 'comment.deleted':
      return `${actor} deleted a comment`;
    case 'sprint.started':
      return `${actor} started sprint "${payloadString(payload, 'sprint_name')}"`;
    case 'sprint.completed':
      return `${actor} completed sprint "${payloadString(payload, 'sprint_name')}"`;
    default:
      return `${actor} ${humanizeEventType(event.eventType)}`;
  }
}
