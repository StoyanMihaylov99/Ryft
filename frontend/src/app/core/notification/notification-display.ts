import { Notification } from './models';

/**
 * Pure display/derivation logic for a `Notification`, kept independently testable — mirrors
 * `core/project/permissions.ts`'s convention of centralizing this kind of thing in one small file
 * rather than re-deriving it inline in `notification-bell.ts`.
 */

export function notificationText(notification: Notification): string {
  const actor = notification.actorDisplayName ?? 'Someone';
  switch (notification.type) {
    case 'MENTION':
      return `${actor} mentioned you in ${notification.issueKey}`;
    case 'ASSIGNED':
      return `${actor} assigned you to ${notification.issueKey}`;
    case 'STATUS_CHANGED':
      return `${actor} changed the status of ${notification.issueKey}`;
    case 'COMMENT':
      return `${actor} commented on ${notification.issueKey}`;
  }
}

/** Ryft issue keys are always `{PROJECTKEY}-{n}` — the project key is everything before the last
 *  hyphen, since a project key itself can't contain one (see `ProjectKeyAlreadyExistsException`'s
 *  validation), so the *last* hyphen always separates the key from the issue number. */
export function projectKeyFromIssueKey(issueKey: string): string {
  return issueKey.slice(0, issueKey.lastIndexOf('-'));
}
