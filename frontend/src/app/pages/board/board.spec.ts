import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { Board as BoardModel } from '../../core/board/models';
import { Issue } from '../../core/issue/models';
import { ProjectMember } from '../../core/project/models';
import { Board } from './board';

function projectMember(userId: string, role: ProjectMember['role']): ProjectMember {
  return { userId, email: 'x@example.com', displayName: 'X', avatarUrl: null, role, addedAt: '2024-01-01T00:00:00Z' };
}

function issue(key: string, status: Issue['status']): Issue {
  return {
    id: key,
    projectId: 'p1',
    key,
    type: 'TASK',
    title: `Title ${key}`,
    description: null,
    status,
    priority: 'MEDIUM',
    assigneeId: null,
    reporterId: 'u1',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: null,
    resolvedAt: null,
  };
}

function boardWith(...issues: Issue[]): BoardModel {
  const columns: BoardModel['columns'] = [
    { statusId: 'todo', name: 'To Do', category: 'TODO', issues: [] },
    { statusId: 'inprogress', name: 'In Progress', category: 'IN_PROGRESS', issues: [] },
    { statusId: 'done', name: 'Done', category: 'DONE', issues: [] },
  ];
  for (const value of issues) {
    columns.find((column) => column.category === value.status)!.issues.push(value);
  }
  return { projectId: 'p1', projectKey: 'TRK', columns };
}

function dropEvent(
  previousData: Issue[],
  containerData: Issue[],
  previousIndex: number,
  currentIndex: number,
  sameContainer: boolean,
): CdkDragDrop<Issue[]> {
  const previousContainer = { data: previousData };
  const container = sameContainer ? previousContainer : { data: containerData };
  return {
    previousContainer,
    container,
    previousIndex,
    currentIndex,
    item: {},
    isPointerOverContainer: true,
    distance: { x: 0, y: 0 },
    dropPoint: { x: 0, y: 0 },
    event: new MouseEvent('mouseup'),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  } as any;
}

describe('Board', () => {
  let fixture: ComponentFixture<Board>;
  let component: Board;
  let httpMock: HttpTestingController;
  let currentUserId: string | null;

  beforeEach(async () => {
    currentUserId = null;
    await TestBed.configureTestingModule({
      imports: [Board],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ projectKey: 'TRK' }) } },
        },
        {
          provide: AuthService,
          useValue: { currentUser: () => (currentUserId ? { id: currentUserId } : null) },
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Board);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialBoard(board: BoardModel, members: ProjectMember[] = []): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/board`).flush(board);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/members`).flush(members);
  }

  it('loads the board on creation', () => {
    flushInitialBoard(boardWith(issue('TRK-1', 'TODO')));

    expect(component.loading()).toBe(false);
    expect(component.board()?.columns[0].issues).toHaveLength(1);
  });

  it('canManageIssues is true for an Owner', () => {
    currentUserId = 'u1';
    flushInitialBoard(boardWith(), [projectMember('u1', 'OWNER')]);

    expect(component.canManageIssues()).toBe(true);
  });

  it('canManageIssues is true for an Admin', () => {
    currentUserId = 'u1';
    flushInitialBoard(boardWith(), [projectMember('u1', 'ADMIN')]);

    expect(component.canManageIssues()).toBe(true);
  });

  it('canManageIssues is false for a plain Member', () => {
    currentUserId = 'u1';
    flushInitialBoard(boardWith(), [projectMember('u1', 'MEMBER')]);

    expect(component.canManageIssues()).toBe(false);
  });

  it('canManageIssues stays false when the members request fails', () => {
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/board`).flush(boardWith());
    httpMock
      .expectOne(`${environment.apiBaseUrl}/projects/TRK/members`)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    expect(component.canManageIssues()).toBe(false);
  });

  it('isOwner is true only for an Owner, and members() reflects the loaded list', () => {
    currentUserId = 'u1';
    const roster = [projectMember('u1', 'OWNER'), projectMember('u2', 'MEMBER')];
    flushInitialBoard(boardWith(), roster);

    expect(component.isOwner()).toBe(true);
    expect(component.canAddMembers()).toBe(true);
    expect(component.members()).toEqual(roster);
  });

  it('isOwner is false for an Admin, though they can still add members', () => {
    currentUserId = 'u1';
    flushInitialBoard(boardWith(), [projectMember('u1', 'ADMIN')]);

    expect(component.isOwner()).toBe(false);
    expect(component.canAddMembers()).toBe(true);
  });

  it('submitInvite adds the returned member to the list on success', () => {
    currentUserId = 'u1';
    flushInitialBoard(boardWith(), [projectMember('u1', 'OWNER')]);

    component.inviteForm.setValue({ email: 'new@example.com', role: 'MEMBER' });
    component.submitInvite();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/members`);
    expect(req.request.body).toEqual({ email: 'new@example.com', role: 'MEMBER' });
    req.flush(projectMember('u2', 'MEMBER'));

    expect(component.members().map((m) => m.userId)).toEqual(['u1', 'u2']);
    expect(component.inviting()).toBe(false);
    expect(component.inviteForm.value.email).toBe('');
  });

  it('submitInvite reports a friendly message when the target user does not exist', () => {
    currentUserId = 'u1';
    flushInitialBoard(boardWith(), [projectMember('u1', 'OWNER')]);

    component.inviteForm.setValue({ email: 'ghost@example.com', role: 'MEMBER' });
    component.submitInvite();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/projects/TRK/members`)
      .flush({ message: 'not found' }, { status: 404, statusText: 'Not Found' });

    expect(component.memberError()).toContain('register first');
  });

  it('submitInvite reports a friendly message when the user is already a member', () => {
    currentUserId = 'u1';
    flushInitialBoard(boardWith(), [projectMember('u1', 'OWNER')]);

    component.inviteForm.setValue({ email: 'u2@example.com', role: 'MEMBER' });
    component.submitInvite();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/projects/TRK/members`)
      .flush({ message: 'conflict' }, { status: 409, statusText: 'Conflict' });

    expect(component.memberError()).toContain('already a member');
  });

  it('changeMemberRole updates optimistically and reverts on failure', () => {
    currentUserId = 'u1';
    flushInitialBoard(boardWith(), [projectMember('u1', 'OWNER'), projectMember('u2', 'MEMBER')]);

    component.changeMemberRole('u2', 'ADMIN');
    expect(component.members().find((m) => m.userId === 'u2')?.role).toBe('ADMIN');

    httpMock
      .expectOne(`${environment.apiBaseUrl}/projects/TRK/members/u2`)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    expect(component.members().find((m) => m.userId === 'u2')?.role).toBe('MEMBER');
    expect(component.memberError()).toContain('Failed to change role');
  });

  it('removeMember removes optimistically and restores the list on failure', () => {
    currentUserId = 'u1';
    flushInitialBoard(boardWith(), [projectMember('u1', 'OWNER'), projectMember('u2', 'MEMBER')]);

    component.removeMember('u2');
    expect(component.members().map((m) => m.userId)).toEqual(['u1']);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/projects/TRK/members/u2`)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    expect(component.members().map((m) => m.userId)).toEqual(['u1', 'u2']);
    expect(component.memberError()).toContain('Failed to remove member');
  });

  it('reorders within the same column without calling the API', () => {
    const todoIssue1 = issue('TRK-1', 'TODO');
    const todoIssue2 = issue('TRK-2', 'TODO');
    flushInitialBoard(boardWith(todoIssue1, todoIssue2));
    const column = component.board()!.columns[0];

    component.drop(dropEvent(column.issues, column.issues, 0, 1, true), column);

    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1/status`);
    expect(column.issues.map((i) => i.key)).toEqual(['TRK-2', 'TRK-1']);
  });

  it('moving to another column calls the status endpoint', () => {
    flushInitialBoard(boardWith(issue('TRK-1', 'TODO')));
    const board = component.board()!;
    const todoColumn = board.columns[0];
    const inProgressColumn = board.columns[1];

    component.drop(dropEvent(todoColumn.issues, inProgressColumn.issues, 0, 0, false), inProgressColumn);

    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/status`).flush(issue('TRK-1', 'IN_PROGRESS'));
    expect(todoColumn.issues).toHaveLength(0);
    expect(inProgressColumn.issues.map((i) => i.key)).toEqual(['TRK-1']);
  });

  it('reverts the move when the status endpoint call fails', () => {
    flushInitialBoard(boardWith(issue('TRK-1', 'TODO')));
    const board = component.board()!;
    const todoColumn = board.columns[0];
    const inProgressColumn = board.columns[1];

    component.drop(dropEvent(todoColumn.issues, inProgressColumn.issues, 0, 0, false), inProgressColumn);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-1/status`)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    expect(inProgressColumn.issues).toHaveLength(0);
    expect(todoColumn.issues.map((i) => i.key)).toEqual(['TRK-1']);
    expect(component.errorMessage()).toContain('TRK-1');
  });

  it('creating an issue adds it to the To Do column', () => {
    flushInitialBoard(boardWith());

    component.createForm.setValue({ type: 'BUG', title: 'New bug' });
    component.submitCreate();

    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/issues`).flush(issue('TRK-1', 'TODO'));

    expect(component.board()!.columns[0].issues.map((i) => i.key)).toEqual(['TRK-1']);
    expect(component.showCreateForm()).toBe(false);
  });

  it('moves an updated issue into its new column when the status changed via the detail panel', () => {
    flushInitialBoard(boardWith(issue('TRK-1', 'TODO')));

    component.onIssueUpdated(issue('TRK-1', 'DONE'));

    const board = component.board()!;
    expect(board.columns[0].issues).toHaveLength(0);
    expect(board.columns[2].issues.map((i) => i.key)).toEqual(['TRK-1']);
  });

  it('removes a deleted issue and closes the panel', () => {
    flushInitialBoard(boardWith(issue('TRK-1', 'TODO')));
    component.openIssue('TRK-1');

    component.onIssueDeleted('TRK-1');

    expect(component.board()!.columns[0].issues).toHaveLength(0);
    expect(component.selectedIssueKey()).toBeNull();
  });
});
