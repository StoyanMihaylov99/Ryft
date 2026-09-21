import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { WebsocketService } from '../websocket/websocket.service';
import { Notification } from './models';
import { NotificationService } from './notification.service';

function notification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: 'n1',
    type: 'COMMENT',
    issueId: 'i1',
    issueKey: 'TRK-1',
    actorId: 'u1',
    actorDisplayName: 'Ada',
    readAt: null,
    createdAt: '2024-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('NotificationService', () => {
  let httpMock: HttpTestingController;
  let isAuthenticated: WritableSignal<boolean>;
  let notificationPushes$: Subject<Notification>;

  beforeEach(() => {
    isAuthenticated = signal(false);
    notificationPushes$ = new Subject<Notification>();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { isAuthenticated } },
        {
          provide: WebsocketService,
          useValue: { watchNotifications: () => notificationPushes$, watchProjectBoard: () => notificationPushes$ },
        },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('does not seed until the user is authenticated', () => {
    TestBed.inject(NotificationService);
    TestBed.tick();

    httpMock.expectNone(`${environment.apiBaseUrl}/notifications`);
  });

  it('seeds from GET /notifications once authenticated', () => {
    const service = TestBed.inject(NotificationService);
    isAuthenticated.set(true);
    TestBed.tick();

    httpMock.expectOne(`${environment.apiBaseUrl}/notifications`).flush([notification()]);

    expect(service.notifications()).toEqual([notification()]);
    expect(service.unreadCount()).toBe(1);
  });

  it('unreadCount counts only notifications with a null readAt', () => {
    const service = TestBed.inject(NotificationService);
    isAuthenticated.set(true);
    TestBed.tick();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/notifications`)
      .flush([notification({ id: 'n1', readAt: null }), notification({ id: 'n2', readAt: '2024-01-02T00:00:00Z' })]);

    expect(service.unreadCount()).toBe(1);
  });

  it('prepends a live push received over the websocket', () => {
    const service = TestBed.inject(NotificationService);
    isAuthenticated.set(true);
    TestBed.tick();
    httpMock.expectOne(`${environment.apiBaseUrl}/notifications`).flush([notification({ id: 'old' })]);

    notificationPushes$.next(notification({ id: 'new' }));

    expect(service.notifications().map((n) => n.id)).toEqual(['new', 'old']);
  });

  it('markRead updates optimistically and keeps the server response', () => {
    const service = TestBed.inject(NotificationService);
    isAuthenticated.set(true);
    TestBed.tick();
    httpMock.expectOne(`${environment.apiBaseUrl}/notifications`).flush([notification()]);

    service.markRead('n1');
    expect(service.notifications()[0].readAt).not.toBeNull();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/notifications/n1/read`);
    expect(req.request.method).toBe('PATCH');
    req.flush(notification({ readAt: '2024-01-03T00:00:00Z' }));

    expect(service.notifications()[0].readAt).toBe('2024-01-03T00:00:00Z');
  });

  it('markRead reverts the optimistic update on failure', () => {
    const service = TestBed.inject(NotificationService);
    isAuthenticated.set(true);
    TestBed.tick();
    httpMock.expectOne(`${environment.apiBaseUrl}/notifications`).flush([notification()]);

    service.markRead('n1');
    httpMock
      .expectOne(`${environment.apiBaseUrl}/notifications/n1/read`)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    expect(service.notifications()[0].readAt).toBeNull();
  });

  it('markRead is a no-op for an already-read notification', () => {
    const service = TestBed.inject(NotificationService);
    isAuthenticated.set(true);
    TestBed.tick();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/notifications`)
      .flush([notification({ readAt: '2024-01-01T00:00:00Z' })]);

    service.markRead('n1');

    httpMock.expectNone(`${environment.apiBaseUrl}/notifications/n1/read`);
  });

  it('markAllRead marks every unread notification and is a no-op when none are unread', () => {
    const service = TestBed.inject(NotificationService);
    isAuthenticated.set(true);
    TestBed.tick();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/notifications`)
      .flush([notification({ id: 'n1' }), notification({ id: 'n2' })]);

    service.markAllRead();
    expect(service.unreadCount()).toBe(0);

    httpMock.expectOne(`${environment.apiBaseUrl}/notifications/read-all`).flush(null);

    service.markAllRead();
    httpMock.expectNone(`${environment.apiBaseUrl}/notifications/read-all`);
  });

  it('markAllRead reverts on failure', () => {
    const service = TestBed.inject(NotificationService);
    isAuthenticated.set(true);
    TestBed.tick();
    httpMock.expectOne(`${environment.apiBaseUrl}/notifications`).flush([notification()]);

    service.markAllRead();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/notifications/read-all`)
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    expect(service.unreadCount()).toBe(1);
  });

  it('clears the list when the user signs out', () => {
    const service = TestBed.inject(NotificationService);
    isAuthenticated.set(true);
    TestBed.tick();
    httpMock.expectOne(`${environment.apiBaseUrl}/notifications`).flush([notification()]);

    isAuthenticated.set(false);
    TestBed.tick();

    expect(service.notifications()).toEqual([]);
  });
});
