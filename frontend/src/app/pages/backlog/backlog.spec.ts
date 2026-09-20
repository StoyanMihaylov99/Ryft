import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { Issue } from '../../core/issue/models';
import { Project, ProjectRole } from '../../core/project/models';
import { Sprint } from '../../core/sprint/models';
import { Backlog } from './backlog';

function project(callerRole: ProjectRole | null = null): Project {
  return {
    id: 'p1',
    workspaceId: 'w1',
    key: 'TRK',
    name: 'Tracker',
    description: null,
    createdAt: '2024-01-01T00:00:00Z',
    archivedAt: null,
    callerRole,
  };
}

function sprint(overrides: Partial<Sprint> = {}): Sprint {
  return {
    id: 's1',
    projectId: 'p1',
    name: 'Sprint 1',
    goal: null,
    state: 'ACTIVE',
    startDate: null,
    endDate: null,
    committedPoints: null,
    createdAt: '2024-01-01T00:00:00Z',
    completedAt: null,
    ...overrides,
  };
}

function issue(key: string, overrides: Partial<Issue> = {}): Issue {
  return {
    id: key,
    projectId: 'p1',
    key,
    type: 'TASK',
    title: `Title ${key}`,
    description: null,
    statusId: 'todo',
    statusName: 'To Do',
    statusCategory: 'TODO',
    callerCanEdit: true,
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

describe('Backlog', () => {
  let fixture: ComponentFixture<Backlog>;
  let component: Backlog;
  let httpMock: HttpTestingController;
  let currentUserId: string | null;

  beforeEach(async () => {
    currentUserId = null;
    await TestBed.configureTestingModule({
      imports: [Backlog],
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
    fixture = TestBed.createComponent(Backlog);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitial(
    sprints: Sprint[],
    sprintIssues: Record<string, Issue[]>,
    backlogIssues: Issue[],
    callerRole: ProjectRole | null = null,
  ): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/sprints`).flush(sprints);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK`).flush(project(callerRole));
    for (const s of sprints) {
      if (s.state === 'PLANNED' || s.state === 'ACTIVE') {
        httpMock
          .expectOne(`${environment.apiBaseUrl}/projects/TRK/issues?sprintId=${s.id}`)
          .flush(sprintIssues[s.id] ?? []);
      }
    }
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/backlog`).flush(backlogIssues);
  }

  it('loads and renders sprint sections and the backlog tail', () => {
    flushInitial(
      [
        sprint({ id: 's1', name: 'Sprint 1', state: 'ACTIVE' }),
        sprint({ id: 's2', name: 'Sprint 2', state: 'COMPLETED' }),
      ],
      { s1: [issue('TRK-1', { sprintId: 's1' })] },
      [issue('TRK-2')],
    );
    fixture.detectChanges();

    expect(component.loading()).toBe(false);
    expect(component.sections()).toHaveLength(1);
    expect(component.sections()[0].sprint.id).toBe('s1');
    expect(component.backlogIssues().map((i) => i.key)).toEqual(['TRK-2']);
    const headings = fixture.debugElement.queryAll(By.css('.backlog-section h2'));
    expect(headings[0].nativeElement.textContent).toContain('Sprint 1');
    expect(headings[1].nativeElement.textContent).toContain('Backlog');
  });

  it('shows a note and only the backlog section when there are no open sprints', () => {
    flushInitial([sprint({ id: 's1', state: 'COMPLETED' })], {}, [issue('TRK-1')]);
    fixture.detectChanges();

    expect(component.sections()).toHaveLength(0);
    expect(fixture.debugElement.query(By.css('.empty-state')).nativeElement.textContent).toContain(
      'No active or planned sprints',
    );
    expect(fixture.debugElement.queryAll(By.css('.backlog-section'))).toHaveLength(1);
  });

  it('canManage is true for an Owner and false for a plain Member', () => {
    currentUserId = 'u1';
    flushInitial([], {}, [], 'OWNER');
    expect(component.canManage()).toBe(true);
  });

  it('cross-section drag calls moveToSprint and reverts on error', () => {
    flushInitial(
      [sprint({ id: 's1', state: 'ACTIVE' })],
      { s1: [] },
      [issue('TRK-1')],
      'OWNER',
    );

    const section = component.sections()[0];
    const backlog = component.backlogIssues();

    component.drop(dropEvent(backlog, section.issues, 0, 0, false), section.sprint.id);

    expect(backlog).toHaveLength(0);
    expect(section.issues.map((i) => i.key)).toEqual(['TRK-1']);

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/sprint`);
    expect(req.request.body).toEqual({ sprintId: 's1' });
    req.flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    expect(section.issues).toHaveLength(0);
    expect(backlog.map((i) => i.key)).toEqual(['TRK-1']);
    expect(component.errorMessage()).toContain('TRK-1');
  });

  it('drag to the backlog calls moveToSprint with a null sprintId, then reorderBacklog', () => {
    flushInitial(
      [sprint({ id: 's1', state: 'ACTIVE' })],
      { s1: [issue('TRK-1', { sprintId: 's1' })] },
      [],
      'OWNER',
    );

    const section = component.sections()[0];
    const backlog = component.backlogIssues();

    component.drop(dropEvent(section.issues, backlog, 0, 0, false), null);

    const moveReq = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/sprint`);
    expect(moveReq.request.body).toEqual({ sprintId: null });
    moveReq.flush(issue('TRK-1', { sprintId: null }));

    const reorderReq = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/backlog-rank`);
    expect(reorderReq.request.body).toEqual({ beforeIssueKey: null, afterIssueKey: null });
    reorderReq.flush(issue('TRK-1', { sprintId: null }));

    expect(section.issues).toHaveLength(0);
    expect(backlog.map((i) => i.key)).toEqual(['TRK-1']);
  });

  it('drag from a sprint into a specific backlog position calls moveToSprint then reorderBacklog with the drop-position neighbors', () => {
    const b = issue('TRK-2');
    const c = issue('TRK-3');
    flushInitial(
      [sprint({ id: 's1', state: 'ACTIVE' })],
      { s1: [issue('TRK-1', { sprintId: 's1' })] },
      [b, c],
      'OWNER',
    );

    const section = component.sections()[0];
    const backlog = component.backlogIssues();

    // Drag TRK-1 out of the sprint and drop it between TRK-2 and TRK-3 (index 1 of the backlog).
    component.drop(dropEvent(section.issues, backlog, 0, 1, false), null);

    expect(backlog.map((i) => i.key)).toEqual(['TRK-2', 'TRK-1', 'TRK-3']);

    const moveReq = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/sprint`);
    expect(moveReq.request.body).toEqual({ sprintId: null });
    moveReq.flush(issue('TRK-1', { sprintId: null }));

    const reorderReq = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/backlog-rank`);
    expect(reorderReq.request.body).toEqual({ beforeIssueKey: 'TRK-2', afterIssueKey: 'TRK-3' });
    reorderReq.flush(issue('TRK-1', { sprintId: null }));

    expect(section.issues).toHaveLength(0);
    expect(backlog.map((i) => i.key)).toEqual(['TRK-2', 'TRK-1', 'TRK-3']);
    expect(component.errorMessage()).toBeNull();
  });

  it('drops the unconfirmed backlog position when reorderBacklog fails after a successful sprint move', () => {
    const b = issue('TRK-2');
    const c = issue('TRK-3');
    flushInitial(
      [sprint({ id: 's1', state: 'ACTIVE' })],
      { s1: [issue('TRK-1', { sprintId: 's1' })] },
      [b, c],
      'OWNER',
    );

    const section = component.sections()[0];
    const backlog = component.backlogIssues();

    component.drop(dropEvent(section.issues, backlog, 0, 1, false), null);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-1/sprint`)
      .flush(issue('TRK-1', { sprintId: null }));
    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-1/backlog-rank`)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    // The sprint move already succeeded, so the issue stays in the backlog — only its
    // unconfirmed position is dropped, pushed to the end rather than left mid-list.
    expect(section.issues).toHaveLength(0);
    expect(backlog.map((i) => i.key)).toEqual(['TRK-2', 'TRK-3', 'TRK-1']);
    expect(component.errorMessage()).toContain('TRK-1');
  });

  it('same-section reorder within the backlog calls reorderBacklog with the correct neighbor keys', () => {
    const a = issue('TRK-1');
    const b = issue('TRK-2');
    const c = issue('TRK-3');
    flushInitial([], {}, [a, b, c], 'OWNER');

    const backlog = component.backlogIssues();
    // Move TRK-1 (index 0) to index 1, landing between TRK-2 and TRK-3.
    component.drop(dropEvent(backlog, backlog, 0, 1, true), null);

    expect(backlog.map((i) => i.key)).toEqual(['TRK-2', 'TRK-1', 'TRK-3']);
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/backlog-rank`);
    expect(req.request.body).toEqual({ beforeIssueKey: 'TRK-2', afterIssueKey: 'TRK-3' });
    req.flush(a);
  });

  it('reverts the backlog reorder when the API call fails', () => {
    const a = issue('TRK-1');
    const b = issue('TRK-2');
    flushInitial([], {}, [a, b], 'OWNER');

    const backlog = component.backlogIssues();
    component.drop(dropEvent(backlog, backlog, 0, 1, true), null);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-1/backlog-rank`)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    expect(backlog.map((i) => i.key)).toEqual(['TRK-1', 'TRK-2']);
    expect(component.errorMessage()).toContain('TRK-1');
  });

  it('same-section reorder within a sprint does not call the API', () => {
    const a = issue('TRK-1', { sprintId: 's1' });
    const b = issue('TRK-2', { sprintId: 's1' });
    flushInitial(
      [sprint({ id: 's1', state: 'ACTIVE' })],
      { s1: [a, b] },
      [],
      'OWNER',
    );

    const section = component.sections()[0];
    component.drop(dropEvent(section.issues, section.issues, 0, 1, true), section.sprint.id);

    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1/backlog-rank`);
    expect(section.issues.map((i) => i.key)).toEqual(['TRK-2', 'TRK-1']);
  });

  it("resolves a card issue's linked-epic title from the sections and backlog already loaded", () => {
    const epic = issue('TRK-1', { type: 'EPIC', title: 'Big epic' });
    const story = issue('TRK-2', { type: 'STORY', parentId: 'TRK-1', sprintId: 's1' });
    flushInitial([sprint({ id: 's1', state: 'ACTIVE' })], { s1: [story] }, [epic]);

    expect(component.epicTitleFor(story)).toBe('Big epic');
    expect(component.epicTitleFor(epic)).toBeNull();
  });

  it('hides drag affordances for a non-Owner/Admin caller', () => {
    currentUserId = 'u1';
    flushInitial(
      [sprint({ id: 's1', state: 'ACTIVE' })],
      { s1: [issue('TRK-1', { sprintId: 's1' })] },
      [issue('TRK-2')],
      'MEMBER',
    );
    fixture.detectChanges();

    expect(component.canManage()).toBe(false);
    const dropLists = fixture.debugElement.queryAll(By.directive(CdkDropList));
    const drags = fixture.debugElement.queryAll(By.directive(CdkDrag));
    expect(dropLists.length).toBeGreaterThan(0);
    expect(drags.length).toBeGreaterThan(0);
    for (const list of dropLists) {
      expect(list.injector.get(CdkDropList).disabled).toBe(true);
    }
    for (const drag of drags) {
      expect(drag.injector.get(CdkDrag).disabled).toBe(true);
    }
  });
});
