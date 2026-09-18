import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { Issue } from '../../core/issue/models';
import { ProjectMember } from '../../core/project/models';
import { Burndown, SprintBoard as SprintBoardModel } from '../../core/sprint/models';
import { SprintBoard } from './sprint-board';

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
    sprintId: 's1',
    parentId: null,
    ...overrides,
  };
}

function boardWith(...issues: Issue[]): SprintBoardModel {
  const columns: SprintBoardModel['columns'] = [
    { statusId: 'todo', name: 'To Do', category: 'TODO', issues: [] },
    { statusId: 'inprogress', name: 'In Progress', category: 'IN_PROGRESS', issues: [] },
    { statusId: 'done', name: 'Done', category: 'DONE', issues: [] },
  ];
  for (const value of issues) {
    columns.find((column) => column.category === value.status)!.issues.push(value);
  }
  return { projectId: 'p1', projectKey: 'TRK', sprintId: 's1', sprintName: 'Sprint 1', columns };
}

function burndown(overrides: Partial<Burndown> = {}): Burndown {
  return {
    sprintId: 's1',
    sprintName: 'Sprint 1',
    startDate: '2026-01-01',
    endDate: '2026-01-14',
    committedPoints: 20,
    idealBurndown: [
      { date: '2026-01-01', remainingPoints: 20 },
      { date: '2026-01-14', remainingPoints: 0 },
    ],
    actualBurndown: [{ date: '2026-01-01', remainingPoints: 20 }],
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

describe('SprintBoard', () => {
  let fixture: ComponentFixture<SprintBoard>;
  let component: SprintBoard;
  let httpMock: HttpTestingController;
  let currentUserId: string | null;

  beforeEach(async () => {
    currentUserId = null;
    await TestBed.configureTestingModule({
      imports: [SprintBoard],
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
    fixture = TestBed.createComponent(SprintBoard);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialBoard(board: SprintBoardModel, members: ProjectMember[] = []): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/board/sprint`).flush(board);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/members`).flush(members);
  }

  it('loads the sprint board on creation and renders its columns', () => {
    flushInitialBoard(boardWith(issue('TRK-1', 'TODO')));
    fixture.detectChanges();

    expect(component.loading()).toBe(false);
    expect(component.board()?.columns[0].issues).toHaveLength(1);
    expect(component.board()?.sprintName).toBe('Sprint 1');
    expect(fixture.debugElement.query(By.css('h1')).nativeElement.textContent).toContain(
      'Sprint 1',
    );
  });

  it('canManageIssues is true for an Owner and false for a plain Member', () => {
    currentUserId = 'u1';
    flushInitialBoard(boardWith(), [projectMember('u1', 'OWNER')]);
    expect(component.canManageIssues()).toBe(true);
  });

  it('canManageIssues stays false when the members request fails', () => {
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/board/sprint`).flush(boardWith());
    httpMock
      .expectOne(`${environment.apiBaseUrl}/projects/TRK/members`)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    expect(component.canManageIssues()).toBe(false);
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

  it('shows a "no active sprint" empty state with a link to the Sprints page on a 404', () => {
    httpMock
      .expectOne(`${environment.apiBaseUrl}/projects/TRK/board/sprint`)
      .flush({ message: 'not found' }, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/members`).flush([]);
    fixture.detectChanges();

    expect(component.noActiveSprint()).toBe(true);
    expect(component.errorMessage()).toBeNull();
    const link = fixture.debugElement.query(By.css('.empty-state a'));
    expect(link.nativeElement.getAttribute('href')).toBe('/projects/TRK/sprints');
  });

  it('toggling burndown loads it lazily and renders the chart once resolved', () => {
    flushInitialBoard(boardWith(issue('TRK-1', 'TODO')));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('app-burndown-chart'))).toBeNull();

    component.toggleBurndown();
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/sprints/s1/burndown`).flush(burndown());
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('app-burndown-chart'))).not.toBeNull();
  });

  it("resolves a card issue's linked-epic title from the issues already loaded on the sprint board", () => {
    const epic = issue('TRK-1', 'TODO', { type: 'EPIC', title: 'Big epic' });
    const story = issue('TRK-2', 'TODO', { type: 'STORY', parentId: 'TRK-1' });
    flushInitialBoard(boardWith(epic, story));

    expect(component.epicTitleFor(story)).toBe('Big epic');
    expect(component.epicTitleFor(epic)).toBeNull();
  });

  it('shows a generic error banner for a non-404 failure', () => {
    httpMock
      .expectOne(`${environment.apiBaseUrl}/projects/TRK/board/sprint`)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/members`).flush([]);

    expect(component.noActiveSprint()).toBe(false);
    expect(component.errorMessage()).toBe('Failed to load the sprint board.');
  });
});
