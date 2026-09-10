import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { Login } from './login';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('defaults to the login tab and switches to register on demand', () => {
    expect(component.mode()).toBe('login');
    component.switchMode('register');
    expect(component.mode()).toBe('register');
  });

  it('does not call the API when the login form is invalid', () => {
    component.submitLogin();
    httpMock.expectNone(`${environment.apiBaseUrl}/auth/login`);
    expect(component.loginForm.controls.email.touched).toBe(true);
  });

  it('submits the login form when valid and navigates to the workspace', () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    component.loginForm.setValue({ email: 'user@example.com', password: 'password123' });

    component.submitLogin();

    httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`).flush({
      accessToken: 'token',
      expiresInSeconds: 900,
      user: { id: '1', email: 'user@example.com', displayName: 'User', avatarUrl: null },
    });

    expect(navigateSpy).toHaveBeenCalledWith('/workspace');
  });

  it('shows a friendly message when the OAuth redirect carries an error code', async () => {
    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ error: 'oauth_login_failed' }) } },
        },
      ],
    }).compileComponents();

    const errorFixture = TestBed.createComponent(Login);
    expect(errorFixture.componentInstance.errorMessage()).toContain('Sign-in');
  });
});
