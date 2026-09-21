import { notificationText, projectKeyFromIssueKey } from './notification-display';
import { Notification } from './models';

function notification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: 'n1',
    type: 'COMMENT',
    issueId: 'i1',
    issueKey: 'TRK-142',
    actorId: 'u1',
    actorDisplayName: 'Ada',
    readAt: null,
    createdAt: '2024-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('notificationText', () => {
  it('describes a MENTION', () => {
    expect(notificationText(notification({ type: 'MENTION' }))).toBe('Ada mentioned you in TRK-142');
  });

  it('describes an ASSIGNED', () => {
    expect(notificationText(notification({ type: 'ASSIGNED' }))).toBe('Ada assigned you to TRK-142');
  });

  it('describes a STATUS_CHANGED', () => {
    expect(notificationText(notification({ type: 'STATUS_CHANGED' }))).toBe(
      'Ada changed the status of TRK-142',
    );
  });

  it('describes a COMMENT', () => {
    expect(notificationText(notification({ type: 'COMMENT' }))).toBe('Ada commented on TRK-142');
  });

  it('falls back to "Someone" when the actor no longer resolves to a user', () => {
    expect(notificationText(notification({ actorDisplayName: null }))).toBe(
      'Someone commented on TRK-142',
    );
  });
});

describe('projectKeyFromIssueKey', () => {
  it('derives the project key from an issue key', () => {
    expect(projectKeyFromIssueKey('TRK-142')).toBe('TRK');
  });

  it('handles a multi-letter project key', () => {
    expect(projectKeyFromIssueKey('BOARD-7')).toBe('BOARD');
  });
});
