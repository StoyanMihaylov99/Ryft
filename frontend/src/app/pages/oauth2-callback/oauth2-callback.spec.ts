import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { Oauth2Callback } from './oauth2-callback';

describe('Oauth2Callback', () => {
  let fixture: ComponentFixture<Oauth2Callback>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Oauth2Callback],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('navigates to /workspace after a successful refresh', () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Oauth2Callback);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/auth/refresh`).flush({
      accessToken: 'token',
      expiresInSeconds: 900,
      user: { id: '1', email: 'a@example.com', displayName: 'A', avatarUrl: null },
    });

    expect(navigateSpy).toHaveBeenCalledWith('/workspace');
  });

  it('navigates to /login?error=oauth when refresh fails', () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Oauth2Callback);
    fixture.detectChanges();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/auth/refresh`)
      .flush({ message: 'unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(navigateSpy).toHaveBeenCalledWith('/login?error=oauth');
  });
});
