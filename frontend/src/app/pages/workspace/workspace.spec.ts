import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { Workspace } from './workspace';

describe('Workspace', () => {
  let component: Workspace;
  let fixture: ComponentFixture<Workspace>;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Workspace],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(Workspace);
    component = fixture.componentInstance;
  }

  it('loads members on creation', () => {
    createComponent();

    httpMock.expectOne(`${environment.apiBaseUrl}/workspace/members`).flush([]);

    expect(component).toBeTruthy();
    expect(component.members()).toEqual([]);
    expect(component.loading()).toBe(false);
  });

  it('shows the not-a-member state when the API returns 403', () => {
    createComponent();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/workspace/members`)
      .flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });

    expect(component.notAMember()).toBe(true);
  });

  it('derives the current user’s role and invite permission once members load', () => {
    createComponent();
    httpMock.expectOne(`${environment.apiBaseUrl}/workspace/members`).flush([
      {
        userId: 'u1',
        email: 'owner@example.com',
        displayName: 'Owner',
        avatarUrl: null,
        role: 'OWNER',
        joinedAt: '2024-01-01T00:00:00Z',
      },
    ]);

    authService.login({ email: 'owner@example.com', password: 'password123' }).subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`).flush({
      accessToken: 'token',
      expiresInSeconds: 900,
      user: { id: 'u1', email: 'owner@example.com', displayName: 'Owner', avatarUrl: null },
    });

    expect(component.myRole()).toBe('OWNER');
    expect(component.canInvite()).toBe(true);
    expect(component.canChangeRoles()).toBe(true);
  });

  it('does not submit the invite form when invalid', () => {
    createComponent();
    httpMock.expectOne(`${environment.apiBaseUrl}/workspace/members`).flush([]);

    component.submitInvite();

    httpMock.expectNone(`${environment.apiBaseUrl}/workspace/invite`);
    expect(component.inviteForm.controls.email.touched).toBe(true);
  });

  it('shows the welcome setup screen when the API returns 404', () => {
    createComponent();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/workspace/members`)
      .flush({ message: 'not set up' }, { status: 404, statusText: 'Not Found' });

    expect(component.setupRequired()).toBe(true);
  });

  it('auto-derives the slug from the workspace name until the slug is edited by hand', () => {
    createComponent();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/workspace/members`)
      .flush({ message: 'not set up' }, { status: 404, statusText: 'Not Found' });

    component.setupForm.controls.name.setValue('Acme Inc');
    expect(component.setupForm.controls.slug.value).toBe('acme-inc');

    component.setupForm.controls.slug.setValue('custom-slug');
    component.setupForm.controls.name.setValue('Something Else');
    expect(component.setupForm.controls.slug.value).toBe('custom-slug');
  });

  it('completes setup and reloads the member list on success', () => {
    createComponent();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/workspace/members`)
      .flush({ message: 'not set up' }, { status: 404, statusText: 'Not Found' });

    component.setupForm.setValue({ name: 'Acme Inc', slug: 'acme-inc' });
    component.submitSetup();

    httpMock.expectOne(`${environment.apiBaseUrl}/workspace/setup`).flush({
      userId: 'u1',
      email: 'owner@example.com',
      displayName: 'Owner',
      avatarUrl: null,
      role: 'OWNER',
      joinedAt: '2024-01-01T00:00:00Z',
    });

    expect(component.setupRequired()).toBe(false);
    httpMock.expectOne(`${environment.apiBaseUrl}/workspace/members`).flush([]);
  });

  it('does not submit the setup form when invalid', () => {
    createComponent();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/workspace/members`)
      .flush({ message: 'not set up' }, { status: 404, statusText: 'Not Found' });

    component.submitSetup();

    httpMock.expectNone(`${environment.apiBaseUrl}/workspace/setup`);
    expect(component.setupForm.controls.name.touched).toBe(true);
  });
});
