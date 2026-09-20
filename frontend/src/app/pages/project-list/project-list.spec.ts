import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { Project } from '../../core/project/models';
import { ProjectList } from './project-list';

function project(key: string): Project {
  return {
    id: key,
    workspaceId: 'w1',
    key,
    name: `Project ${key}`,
    description: null,
    createdAt: '2024-01-01T00:00:00Z',
    archivedAt: null,
    callerRole: null,
  };
}

describe('ProjectList', () => {
  let fixture: ComponentFixture<ProjectList>;
  let component: ProjectList;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectList],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(ProjectList);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads projects on creation', () => {
    httpMock.expectOne(`${environment.apiBaseUrl}/projects`).flush([project('TRK')]);

    expect(component.loading()).toBe(false);
    expect(component.projects()).toHaveLength(1);
  });

  it('does not submit the create form when invalid', () => {
    httpMock.expectOne(`${environment.apiBaseUrl}/projects`).flush([]);

    component.submitCreate();

    httpMock.expectNone(`${environment.apiBaseUrl}/projects`);
    expect(component.createForm.controls.key.touched).toBe(true);
  });

  it('uppercases the key as it is typed', () => {
    httpMock.expectOne(`${environment.apiBaseUrl}/projects`).flush([]);

    component.createForm.controls.key.setValue('trk');

    expect(component.createForm.controls.key.value).toBe('TRK');
  });

  it('creates a project and navigates to its board', () => {
    httpMock.expectOne(`${environment.apiBaseUrl}/projects`).flush([]);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    component.createForm.setValue({ key: 'TRK', name: 'Tracker', description: '' });
    component.submitCreate();

    httpMock.expectOne(`${environment.apiBaseUrl}/projects`).flush(project('TRK'));

    expect(navigateSpy).toHaveBeenCalledWith('/projects/TRK/board');
  });

  it('shows a conflict message when the key is already taken', () => {
    httpMock.expectOne(`${environment.apiBaseUrl}/projects`).flush([]);

    component.createForm.setValue({ key: 'TRK', name: 'Tracker', description: '' });
    component.submitCreate();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/projects`)
      .flush({ message: 'conflict' }, { status: 409, statusText: 'Conflict' });

    expect(component.errorMessage()).toContain('already exists');
  });
});
