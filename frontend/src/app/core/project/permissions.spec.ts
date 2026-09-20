import { Issue } from '../issue/models';
import {
  canChangeStatus,
  canComment,
  canEditIssue,
  canManageIssues,
  canManageProjectSettings,
  canManageSprints,
  isOwner,
} from './permissions';
import { ProjectRole } from './models';

const roles: (ProjectRole | null)[] = ['OWNER', 'ADMIN', 'MEMBER', 'VIEWER', null];

function issueInvolving(assigneeId: string | null, reporterId: string): Pick<
  Issue,
  'assigneeId' | 'reporterId'
> {
  return { assigneeId, reporterId };
}

describe('permissions', () => {
  describe('canManageIssues / canManageSprints / canManageProjectSettings', () => {
    it('are true only for OWNER and ADMIN', () => {
      for (const role of roles) {
        const expected = role === 'OWNER' || role === 'ADMIN';
        expect(canManageIssues(role)).toBe(expected);
        expect(canManageSprints(role)).toBe(expected);
        expect(canManageProjectSettings(role)).toBe(expected);
      }
    });
  });

  describe('canChangeStatus / canComment', () => {
    it('are true for every role except VIEWER and null', () => {
      expect(canChangeStatus('OWNER')).toBe(true);
      expect(canChangeStatus('ADMIN')).toBe(true);
      expect(canChangeStatus('MEMBER')).toBe(true);
      expect(canChangeStatus('VIEWER')).toBe(false);
      expect(canChangeStatus(null)).toBe(false);

      expect(canComment('OWNER')).toBe(true);
      expect(canComment('ADMIN')).toBe(true);
      expect(canComment('MEMBER')).toBe(true);
      expect(canComment('VIEWER')).toBe(false);
      expect(canComment(null)).toBe(false);
    });
  });

  describe('isOwner', () => {
    it('is true only for OWNER', () => {
      for (const role of roles) {
        expect(isOwner(role)).toBe(role === 'OWNER');
      }
    });
  });

  describe('canEditIssue', () => {
    const involved = issueInvolving('u1', 'u2');
    const notInvolved = issueInvolving('u3', 'u4');
    const unassignedButReported = issueInvolving(null, 'u1');

    it('is always true for OWNER and ADMIN, regardless of involvement', () => {
      expect(canEditIssue('OWNER', notInvolved, 'u1')).toBe(true);
      expect(canEditIssue('ADMIN', notInvolved, 'u1')).toBe(true);
      expect(canEditIssue('OWNER', notInvolved, null)).toBe(true);
    });

    it('is true for a MEMBER who is the assignee', () => {
      expect(canEditIssue('MEMBER', involved, 'u1')).toBe(true);
    });

    it('is true for a MEMBER who is the reporter', () => {
      expect(canEditIssue('MEMBER', unassignedButReported, 'u1')).toBe(true);
    });

    it('is false for a MEMBER who is neither the assignee nor the reporter', () => {
      expect(canEditIssue('MEMBER', notInvolved, 'u1')).toBe(false);
    });

    it('is false for a MEMBER when currentUserId is null', () => {
      expect(canEditIssue('MEMBER', involved, null)).toBe(false);
    });

    it('is always false for VIEWER, even when involved', () => {
      expect(canEditIssue('VIEWER', involved, 'u1')).toBe(false);
    });

    it('is always false when role is null, even when involved', () => {
      expect(canEditIssue(null, involved, 'u1')).toBe(false);
    });

    it('full role x involvement matrix', () => {
      const cases: [ProjectRole | null, boolean, boolean][] = [
        // [role, isAssignee, isReporter]
        ['OWNER', false, false],
        ['ADMIN', false, false],
        ['MEMBER', true, false],
        ['MEMBER', false, true],
        ['MEMBER', false, false],
        ['VIEWER', true, false],
        ['VIEWER', false, true],
        [null, true, false],
      ];
      for (const [role, isAssignee, isReporter] of cases) {
        const issue = issueInvolving(isAssignee ? 'u1' : 'other', isReporter ? 'u1' : 'other');
        const expected =
          role === 'OWNER' || role === 'ADMIN' || (role === 'MEMBER' && (isAssignee || isReporter));
        expect(canEditIssue(role, issue, 'u1')).toBe(expected);
      }
    });
  });
});
