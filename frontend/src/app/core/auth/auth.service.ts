import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, of, shareReplay, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest, User } from './models';

/**
 * The access token lives in memory only (this signal), never localStorage — the only long-lived
 * credential is the refresh token, which lives solely in an httpOnly cookie the backend sets and
 * this code never reads. A page reload wipes the signal but not the cookie, so callers recover a
 * session via {@link refresh} (see authGuard) rather than any client-side storage.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly accessTokenSignal = signal<string | null>(null);
  private readonly userSignal = signal<User | null>(null);

  readonly currentUser = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.accessTokenSignal() !== null);

  /** De-dupes concurrent refresh attempts within this tab (e.g. several requests 401 at once). */
  private refreshInFlight: Observable<AuthResponse> | null = null;

  getAccessToken(): string | null {
    return this.accessTokenSignal();
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/register`, request, { withCredentials: true })
      .pipe(tap((response) => this.applyAuthResponse(response)));
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, request, { withCredentials: true })
      .pipe(tap((response) => this.applyAuthResponse(response)));
  }

  refresh(): Observable<AuthResponse> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }

    const request$ = this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((response) => this.applyAuthResponse(response)),
        catchError((error: unknown) => {
          this.clearSession();
          return throwError(() => error);
        }),
        finalize(() => {
          this.refreshInFlight = null;
        }),
        shareReplay(1),
      );

    this.refreshInFlight = request$;
    return request$;
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${environment.apiBaseUrl}/auth/logout`, {}, { withCredentials: true }).pipe(
      tap(() => this.clearSession()),
      catchError(() => {
        // Logout should never leave the client thinking it's still signed in, even if the network
        // call itself failed.
        this.clearSession();
        return of(void 0);
      }),
    );
  }

  private applyAuthResponse(response: AuthResponse): void {
    this.accessTokenSignal.set(response.accessToken);
    this.userSignal.set(response.user);
  }

  private clearSession(): void {
    this.accessTokenSignal.set(null);
    this.userSignal.set(null);
  }
}
