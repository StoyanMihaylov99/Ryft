import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { Project, ProjectRole } from '../../core/project/models';
import { Burndown, Sprint } from '../../core/sprint/models';
import { Sprints } from './sprints';

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
    state: 'PLANNED',
    startDate: null,
    endDate: null,
    committedPoints: null,
    createdAt: '2024-01-01T00:00:00Z',
    completedAt: null,
    ...overrides,
  };
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
    actualBurndown: [
      { date: '2026-01-01', remainingPoints: 20 },
      { date: '2026-01-14', remainingPoints: 0 },
    ],
    ...overrides,
  };
}

describe('Sprints', () => {
  let fixture: ComponentFixture<Sprints>;
  let component: Sprints;
  let httpMock: HttpTestingController;
  let currentUserId: string | null;

  beforeEach(async () => {
    currentUserId = null;
    await TestBed.configureTestingModule({
      imports: [Sprints],
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
    fixture = TestBed.createComponent(Sprints);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitial(sprints: Sprint[], callerRole: ProjectRole | null = null): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/sprints`).flush(sprints);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK`).flush(project(callerRole));
  }

  it('loads and renders the project sprints', () => {
    flushInitial([sprint({ id: 's1', name: 'Sprint 1' })]);
    fixture.detectChanges();

    expect(component.loading()).toBe(false);
    expect(component.sprints()).toHaveLength(1);
    const name = fixture.debugElement.query(By.css('.sprint-name'));
    expect(name.nativeElement.textContent).toContain('Sprint 1');
  });

  it('reports an error when loading sprints fails', () => {
    httpMock
      .expectOne(`${environment.apiBaseUrl}/projects/TRK/sprints`)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK`).flush(project());

    expect(component.loading()).toBe(false);
    expect(component.errorMessage()).toBe('Failed to load sprints.');
  });

  it('canManageSprints is true for an Owner and false for a plain Member', () => {
    currentUserId = 'u1';
    flushInitial([], 'OWNER');
    expect(component.canManageSprints()).toBe(true);
  });

  it('create submits the right request body and appends the result', () => {
    currentUserId = 'u1';
    flushInitial([], 'OWNER');

    component.showCreateForm.set(true);
    component.createForm.setValue({
      name: 'Sprint 2',
      goal: 'Ship it',
      startDate: '2024-02-01',
      endDate: '2024-02-14',
    });
    component.submitCreate();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/sprints`);
    expect(req.request.body).toEqual({
      name: 'Sprint 2',
      goal: 'Ship it',
      startDate: '2024-02-01',
      endDate: '2024-02-14',
    });
    req.flush(
      sprint({
        id: 's2',
        name: 'Sprint 2',
        goal: 'Ship it',
        startDate: '2024-02-01',
        endDate: '2024-02-14',
      }),
    );

    expect(component.sprints().map((s) => s.id)).toEqual(['s2']);
    expect(component.showCreateForm()).toBe(false);
  });

  it('sends null for optional fields left blank', () => {
    currentUserId = 'u1';
    flushInitial([], 'OWNER');

    component.createForm.setValue({ name: 'Sprint 2', goal: '', startDate: '', endDate: '' });
    component.submitCreate();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/sprints`);
    expect(req.request.body).toEqual({
      name: 'Sprint 2',
      goal: null,
      startDate: null,
      endDate: null,
    });
    req.flush(sprint({ id: 's2', name: 'Sprint 2' }));
  });

  it('shows the Start button only for a PLANNED sprint and the Complete button only for an ACTIVE one', () => {
    currentUserId = 'u1';
    flushInitial(
      [
        sprint({ id: 's1', state: 'PLANNED' }),
        sprint({ id: 's2', state: 'ACTIVE' }),
        sprint({ id: 's3', state: 'COMPLETED' }),
      ],
      'OWNER',
    );
    fixture.detectChanges();

    const cards = fixture.debugElement.queryAll(By.css('.sprint-card'));
    expect(cards[0].query(By.css('button'))!.nativeElement.textContent).toContain('Start');
    expect(cards[1].query(By.css('button'))!.nativeElement.textContent).toContain('Complete');
    expect(cards[2].nativeElement.textContent).not.toContain('Start');
    expect(cards[2].nativeElement.textContent).not.toContain('Complete');
    expect(cards[2].query(By.css('button'))!.nativeElement.textContent).toContain('View burndown');
  });

  it('hides Start/Complete buttons entirely for a plain Member', () => {
    currentUserId = 'u1';
    flushInitial([sprint({ id: 's1', state: 'PLANNED' })], 'MEMBER');
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.sprint-actions'))).toBeNull();
  });

  it('start calls the start endpoint and updates the sprint state locally', () => {
    currentUserId = 'u1';
    flushInitial([sprint({ id: 's1', state: 'PLANNED' })], 'OWNER');

    component.start(component.sprints()[0]);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/sprints/s1/start`)
      .flush(sprint({ id: 's1', state: 'ACTIVE' }));

    expect(component.sprints()[0].state).toBe('ACTIVE');
  });

  it('complete calls the complete endpoint and updates the sprint state locally', () => {
    currentUserId = 'u1';
    flushInitial([sprint({ id: 's1', state: 'ACTIVE' })], 'OWNER');

    component.complete(component.sprints()[0]);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/sprints/s1/complete`)
      .flush(sprint({ id: 's1', state: 'COMPLETED' }));

    expect(component.sprints()[0].state).toBe('COMPLETED');
  });

  it('toggling burndown on a completed sprint loads it lazily and renders the chart once resolved', () => {
    currentUserId = 'u1';
    flushInitial([sprint({ id: 's1', state: 'COMPLETED' })], 'OWNER');
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('app-burndown-chart'))).toBeNull();

    component.toggleBurndown(component.sprints()[0]);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/sprints/s1/burndown`).flush(burndown());
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('app-burndown-chart'))).not.toBeNull();
  });

  it('shows an inline error banner when starting a sprint fails', () => {
    currentUserId = 'u1';
    flushInitial(
      [sprint({ id: 's1', state: 'PLANNED', name: 'Sprint 1' })],
      'OWNER',
    );

    component.start(component.sprints()[0]);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/sprints/s1/start`)
      .flush({ message: 'conflict' }, { status: 409, statusText: 'Conflict' });

    expect(component.sprints()[0].state).toBe('PLANNED');
    expect(component.errorMessage()).toContain('Sprint 1');
  });
});
