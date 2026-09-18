import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { Board as BoardModel } from '../../core/board/models';
import { Issue, Label, ProjectComponent } from '../../core/issue/models';
import { ProjectMember } from '../../core/project/models';
import { Board } from './board';

function projectMember(userId: string, role: ProjectMember['role']): ProjectMember {
  return {
    userId,
    email: 'x@example.com',
    displayName: 'X',
    avatarUrl: null,
    role,
    addedAt: '2024-01-01T00:00:00Z',
  };
}

function issue(key: string, status: Issue['status'], overrides: Partial<Issue> = {}): Issue {
  return {
    id: key,
    projectId: 'p1',
    key,
    type: 'TASK',
    title: `Title ${key}`,
    description: null,
    status,
    priority: 'MEDIUM',
    storyPoints: null,
    assigneeId: null,
    reporterId: 'u1',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: null,
    resolvedAt: null,
    sprintId: null,
    parentId: null,
    labels: [],
    components: [],
    ...overrides,
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
          useValue: {
            currentUser: () => (currentUserId ? { id: currentUserId, displayName: 'X' } : null),
          },
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

  function flushInitialBoard(
    board: BoardModel,
    members: ProjectMember[] = [],
    labels: Label[] = [],
    components: ProjectComponent[] = [],
  ): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/board`).flush(board);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/members`).flush(members);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/labels`).flush(labels);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/components`).flush(components);
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
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/labels`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/components`).flush([]);

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

    component.drop(
      dropEvent(todoColumn.issues, inProgressColumn.issues, 0, 0, false),
      inProgressColumn,
    );

    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-1/status`)
      .flush(issue('TRK-1', 'IN_PROGRESS'));
    expect(todoColumn.issues).toHaveLength(0);
    expect(inProgressColumn.issues.map((i) => i.key)).toEqual(['TRK-1']);
  });

  it('reverts the move when the status endpoint call fails', () => {
    flushInitialBoard(boardWith(issue('TRK-1', 'TODO')));
    const board = component.board()!;
    const todoColumn = board.columns[0];
    const inProgressColumn = board.columns[1];

    component.drop(
      dropEvent(todoColumn.issues, inProgressColumn.issues, 0, 0, false),
      inProgressColumn,
    );

    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-1/status`)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    expect(inProgressColumn.issues).toHaveLength(0);
    expect(todoColumn.issues.map((i) => i.key)).toEqual(['TRK-1']);
    expect(component.errorMessage()).toContain('TRK-1');
  });

  it('creating an issue adds it to the To Do column', () => {
    flushInitialBoard(boardWith());

    component.createForm.setValue({ type: 'BUG', title: 'New bug', parentId: null });
    component.submitCreate();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/issues`);
    expect(req.request.body).toEqual({ type: 'BUG', title: 'New bug' });
    req.flush(issue('TRK-1', 'TODO'));

    expect(component.board()!.columns[0].issues.map((i) => i.key)).toEqual(['TRK-1']);
    expect(component.showCreateForm()).toBe(false);
  });

  it('shows Epic as a selectable type, alongside Story/Task/Bug', () => {
    currentUserId = 'u1';
    flushInitialBoard(boardWith(), [projectMember('u1', 'OWNER')]);
    component.showCreateForm.set(true);
    fixture.detectChanges();

    const typeSelect = fixture.debugElement.queryAll(By.css('.create-card select'))[0];
    const options = typeSelect.queryAll(By.css('option'));
    expect(options.map((option) => option.nativeElement.value)).toEqual([
      'TASK',
      'STORY',
      'BUG',
      'EPIC',
    ]);
  });

  it('shows the Epic select for a Story/Task/Bug and hides it once the type is switched to Epic', () => {
    currentUserId = 'u1';
    flushInitialBoard(boardWith(), [projectMember('u1', 'OWNER')]);
    component.showCreateForm.set(true);
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.epic-field'))).not.toBeNull();

    component.createForm.controls.type.setValue('EPIC');
    component.onCreateTypeChange();
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.epic-field'))).toBeNull();
  });

  it('lists only the Epic-typed issues on the board as Epic options', () => {
    const epic = issue('TRK-1', 'TODO', { type: 'EPIC', title: 'Big epic' });
    flushInitialBoard(boardWith(epic, issue('TRK-2', 'TODO')));
    component.showCreateForm.set(true);
    fixture.detectChanges();

    expect(component.epics().map((e) => e.key)).toEqual(['TRK-1']);
  });

  it('sends parentId when creating a Story linked to an Epic', () => {
    flushInitialBoard(boardWith());

    component.createForm.setValue({ type: 'STORY', title: 'New story', parentId: 'epic-1' });
    component.submitCreate();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/issues`);
    expect(req.request.body).toEqual({ type: 'STORY', title: 'New story', parentId: 'epic-1' });
    req.flush(issue('TRK-1', 'TODO', { type: 'STORY', parentId: 'epic-1' }));
  });

  it('never sends parentId when creating an Epic, even if one was staged before switching types', () => {
    flushInitialBoard(boardWith());

    component.createForm.setValue({ type: 'STORY', title: 'New epic', parentId: 'epic-1' });
    component.createForm.controls.type.setValue('EPIC');
    component.submitCreate();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/issues`);
    expect(req.request.body).toEqual({ type: 'EPIC', title: 'New epic' });
    req.flush(issue('TRK-1', 'TODO', { type: 'EPIC' }));
  });

  it("resolves a card issue's linked-epic title from the issues already loaded on the board", () => {
    const epic = issue('TRK-1', 'TODO', { type: 'EPIC', title: 'Big epic' });
    const story = issue('TRK-2', 'TODO', { type: 'STORY', parentId: 'TRK-1' });
    flushInitialBoard(boardWith(epic, story));

    expect(component.epicTitleFor(story)).toBe('Big epic');
    expect(component.epicTitleFor(epic)).toBeNull();
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

  function label(overrides: Partial<Label> = {}): Label {
    return { id: 'l1', projectId: 'p1', name: 'Frontend', color: '#4287f5', ...overrides };
  }

  function projectComponent(overrides: Partial<ProjectComponent> = {}): ProjectComponent {
    return { id: 'c1', projectId: 'p1', name: 'API', ...overrides };
  }

  describe('client-side board filtering', () => {
    it('matches an issue only when it has the selected label', () => {
      const withLabel = issue('TRK-1', 'TODO', { labels: [label()] });
      const withoutLabel = issue('TRK-2', 'TODO');
      flushInitialBoard(boardWith(withLabel, withoutLabel));

      expect(component.issueMatchesFilter(withLabel)).toBe(true);
      expect(component.issueMatchesFilter(withoutLabel)).toBe(true);

      component.setLabelFilter('l1');

      expect(component.issueMatchesFilter(withLabel)).toBe(true);
      expect(component.issueMatchesFilter(withoutLabel)).toBe(false);
    });

    it('ANDs the label and component filters together', () => {
      const both = issue('TRK-1', 'TODO', {
        labels: [label()],
        components: [projectComponent()],
      });
      const labelOnly = issue('TRK-2', 'TODO', { labels: [label()] });
      flushInitialBoard(boardWith(both, labelOnly));

      component.setLabelFilter('l1');
      component.setComponentFilter('c1');

      expect(component.issueMatchesFilter(both)).toBe(true);
      expect(component.issueMatchesFilter(labelOnly)).toBe(false);
    });

    it('does not remove filtered-out cards from the board model, only hides them', () => {
      const withLabel = issue('TRK-1', 'TODO', { labels: [label()] });
      const withoutLabel = issue('TRK-2', 'TODO');
      flushInitialBoard(boardWith(withLabel, withoutLabel));
      component.setLabelFilter('l1');
      fixture.detectChanges();

      expect(component.board()!.columns[0].issues).toHaveLength(2);
      const hiddenCard = fixture.debugElement
        .queryAll(By.css('.issue-card'))
        .find((el) => el.nativeElement.textContent.includes('TRK-2'));
      expect(hiddenCard!.classes['filtered-out']).toBe(true);
    });

    it('resets the label filter when the filtered label is deleted', () => {
      flushInitialBoard(boardWith(), [], [label()]);

      component.setLabelFilter('l1');
      expect(component.labelFilter()).toBe('l1');

      component.deleteLabel('l1');
      httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/labels/l1`).flush(null);

      expect(component.labelFilter()).toBeNull();
    });

    it('isFiltering reflects whether either filter is active', () => {
      flushInitialBoard(boardWith(), [], [label()], [projectComponent()]);

      expect(component.isFiltering()).toBe(false);

      component.setLabelFilter('l1');
      expect(component.isFiltering()).toBe(true);

      component.setLabelFilter(null);
      component.setComponentFilter('c1');
      expect(component.isFiltering()).toBe(true);

      component.setComponentFilter(null);
      expect(component.isFiltering()).toBe(false);
    });

    it('disables dragging on every card while a filter is active, to avoid CDK measuring hidden cards’ collapsed rects', () => {
      const withLabel = issue('TRK-1', 'TODO', { labels: [label()] });
      flushInitialBoard(boardWith(withLabel), [], [label()]);
      fixture.detectChanges();
      const card = () => fixture.debugElement.query(By.css('.issue-card'));

      expect(card().classes['cdk-drag-disabled']).toBeFalsy();

      component.setLabelFilter('l1');
      fixture.detectChanges();

      expect(card().classes['cdk-drag-disabled']).toBe(true);
    });
  });

  describe('attaching labels/components on create', () => {
    it('sends the toggled labelIds/componentIds when creating an issue', () => {
      flushInitialBoard(boardWith(), [], [label()], [projectComponent()]);

      component.createForm.setValue({ type: 'TASK', title: 'New task', parentId: null });
      component.toggleCreateLabel('l1');
      component.toggleCreateComponent('c1');
      component.submitCreate();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/issues`);
      expect(req.request.body).toEqual({
        type: 'TASK',
        title: 'New task',
        labelIds: ['l1'],
        componentIds: ['c1'],
      });
      req.flush(issue('TRK-1', 'TODO', { labels: [label()], components: [projectComponent()] }));
    });

    it('omits labelIds/componentIds when none are toggled', () => {
      flushInitialBoard(boardWith(), [], [label()]);

      component.createForm.setValue({ type: 'TASK', title: 'New task', parentId: null });
      component.submitCreate();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/issues`);
      expect(req.request.body).toEqual({ type: 'TASK', title: 'New task' });
      req.flush(issue('TRK-1', 'TODO'));
    });

    it('resets the staged label/component selection when the create form is toggled closed', () => {
      flushInitialBoard(boardWith(), [], [label()]);

      component.toggleCreateLabel('l1');
      expect(component.isCreateLabelSelected('l1')).toBe(true);

      component.toggleCreateForm();

      expect(component.isCreateLabelSelected('l1')).toBe(false);
    });
  });

  describe('labels & components management', () => {
    it('adds a newly created label to the list', () => {
      flushInitialBoard(boardWith());

      component.newLabelName.set('Frontend');
      component.newLabelColor.set('#4287f5');
      component.submitNewLabel();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/labels`);
      expect(req.request.body).toEqual({ name: 'Frontend', color: '#4287f5' });
      req.flush(label());

      expect(component.labels()).toEqual([label()]);
      expect(component.newLabelName()).toBe('');
    });

    it('removes a label optimistically and restores it on failure', () => {
      flushInitialBoard(boardWith(), [], [label()]);

      component.deleteLabel('l1');
      expect(component.labels()).toEqual([]);

      httpMock
        .expectOne(`${environment.apiBaseUrl}/projects/TRK/labels/l1`)
        .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

      expect(component.labels()).toEqual([label()]);
      expect(component.labelError()).toContain('Failed to delete');
    });

    it('restores the label filter if deleting the currently-filtered label fails', () => {
      flushInitialBoard(boardWith(), [], [label()]);
      component.setLabelFilter('l1');

      component.deleteLabel('l1');
      expect(component.labelFilter()).toBeNull();

      httpMock
        .expectOne(`${environment.apiBaseUrl}/projects/TRK/labels/l1`)
        .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

      expect(component.labels()).toEqual([label()]);
      expect(component.labelFilter()).toBe('l1');
    });

    it('adds a newly created component to the list', () => {
      flushInitialBoard(boardWith());

      component.newComponentName.set('API');
      component.submitNewComponent();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/components`);
      expect(req.request.body).toEqual({ name: 'API' });
      req.flush(projectComponent());

      expect(component.components()).toEqual([projectComponent()]);
      expect(component.newComponentName()).toBe('');
    });

    it('removes a component optimistically and restores it on failure', () => {
      flushInitialBoard(boardWith(), [], [], [projectComponent()]);

      component.deleteComponent('c1');
      expect(component.components()).toEqual([]);

      httpMock
        .expectOne(`${environment.apiBaseUrl}/projects/TRK/components/c1`)
        .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

      expect(component.components()).toEqual([projectComponent()]);
      expect(component.componentError()).toContain('Failed to delete');
    });

    it('restores the component filter if deleting the currently-filtered component fails', () => {
      flushInitialBoard(boardWith(), [], [], [projectComponent()]);
      component.setComponentFilter('c1');

      component.deleteComponent('c1');
      expect(component.componentFilter()).toBeNull();

      httpMock
        .expectOne(`${environment.apiBaseUrl}/projects/TRK/components/c1`)
        .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

      expect(component.components()).toEqual([projectComponent()]);
      expect(component.componentFilter()).toBe('c1');
    });
  });
});
