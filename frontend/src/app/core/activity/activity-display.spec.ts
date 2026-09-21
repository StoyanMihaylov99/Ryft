import { activityText } from './activity-display';
import { ActivityEvent } from './models';

function event(overrides: Partial<ActivityEvent> = {}): ActivityEvent {
  return {
    id: 'e1',
    projectId: 'p1',
    issueId: 'i1',
    eventType: 'issue.created',
    actorId: 'a1a1a1a1-0000-0000-0000-000000000000',
    timestamp: '2024-01-01T00:00:00Z',
    payload: {},
    ...overrides,
  };
}

describe('activityText', () => {
  it('describes issue.created with the issue type', () => {
    const text = activityText(event({ eventType: 'issue.created', payload: { issue_type: 'STORY' } }));

    expect(text).toContain('User a1a1a1a1');
    expect(text).toContain('created this issue as a STORY');
  });

  it('describes issue.status_changed with the from/to status names', () => {
    const text = activityText(
      event({
        eventType: 'issue.status_changed',
        payload: { from_status: 'To Do', to_status: 'In Progress' },
      }),
    );

    expect(text).toContain('changed the status from To Do to In Progress');
  });

  it('describes an initial assignment when there was no previous assignee', () => {
    const text = activityText(
      event({
        eventType: 'issue.assignee_changed',
        payload: { previous_assignee_id: null, new_assignee_id: 'u1' },
      }),
    );

    expect(text).toContain('assigned this issue');
    expect(text).not.toContain('reassigned');
  });

  it('describes a reassignment when there was a previous assignee', () => {
    const text = activityText(
      event({
        eventType: 'issue.assignee_changed',
        payload: { previous_assignee_id: 'u1', new_assignee_id: 'u2' },
      }),
    );

    expect(text).toContain('reassigned this issue');
  });

  it('describes comment.added with the excerpt', () => {
    const text = activityText(
      event({ eventType: 'comment.added', payload: { comment_id: 'c1', excerpt: 'Nice work' } }),
    );

    expect(text).toContain('commented: "Nice work"');
  });

  it('describes comment.updated with the excerpt', () => {
    const text = activityText(
      event({ eventType: 'comment.updated', payload: { comment_id: 'c1', excerpt: 'Edited' } }),
    );

    expect(text).toContain('edited a comment: "Edited"');
  });

  it('describes comment.deleted without an excerpt', () => {
    const text = activityText(event({ eventType: 'comment.deleted', payload: { comment_id: 'c1' } }));

    expect(text).toContain('deleted a comment');
  });

  it('describes sprint.started with the sprint name', () => {
    const text = activityText(
      event({ eventType: 'sprint.started', payload: { sprint_id: 's1', sprint_name: 'Sprint 1' } }),
    );

    expect(text).toContain('started sprint "Sprint 1"');
  });

  it('describes sprint.completed with the sprint name', () => {
    const text = activityText(
      event({ eventType: 'sprint.completed', payload: { sprint_id: 's1', sprint_name: 'Sprint 1' } }),
    );

    expect(text).toContain('completed sprint "Sprint 1"');
  });

  it('falls back to a humanized event type for anything unrecognized', () => {
    const text = activityText(event({ eventType: 'issue.watcher_added', payload: {} }));

    expect(text).toContain('issue watcher added');
  });
});
