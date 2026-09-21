import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { WebsocketService } from '../websocket/websocket.service';
import { Notification } from './models';

/**
 * Seeds from `GET /notifications` on sign-in, then stays live for the rest of the session via
 * `WebsocketService.watchNotifications()` — this subscription is never torn down (a root-provided
 * service lives for the app's lifetime, same as `WebsocketService` itself).
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly websocketService = inject(WebsocketService);

  private readonly notificationsSignal = signal<Notification[]>([]);
  readonly notifications = this.notificationsSignal.asReadonly();
  readonly unreadCount = computed(
    () => this.notificationsSignal().filter((notification) => notification.readAt === null).length,
  );

  constructor() {
    effect(() => {
      if (this.authService.isAuthenticated()) {
        this.loadInitial();
      } else {
        this.notificationsSignal.set([]);
      }
    });

    this.websocketService.watchNotifications().subscribe((notification) => {
      this.notificationsSignal.update((notifications) => [notification, ...notifications]);
    });
  }

  private loadInitial(): void {
    this.http.get<Notification[]>(`${environment.apiBaseUrl}/notifications`).subscribe({
      next: (notifications) => this.notificationsSignal.set(notifications),
      // Non-critical: the bell just shows an empty list until the next successful load.
      error: () => {},
    });
  }

  /** Optimistic — marks read locally immediately, reverts if the PATCH fails (mirrors
   *  `Board.changeMemberRole`'s revert-on-error shape). A no-op if already read. */
  markRead(id: string): void {
    const previous = this.notificationsSignal();
    const target = previous.find((notification) => notification.id === id);
    if (!target || target.readAt !== null) {
      return;
    }
    this.notificationsSignal.set(
      previous.map((notification) =>
        notification.id === id
          ? { ...notification, readAt: new Date().toISOString() }
          : notification,
      ),
    );
    this.http.patch<Notification>(`${environment.apiBaseUrl}/notifications/${id}/read`, {}).subscribe({
      next: (updated) =>
        this.notificationsSignal.update((notifications) =>
          notifications.map((notification) => (notification.id === id ? updated : notification)),
        ),
      error: () => this.notificationsSignal.set(previous),
    });
  }

  markAllRead(): void {
    if (this.unreadCount() === 0) {
      return;
    }
    const previous = this.notificationsSignal();
    const optimisticReadAt = new Date().toISOString();
    this.notificationsSignal.set(
      previous.map((notification) =>
        notification.readAt === null ? { ...notification, readAt: optimisticReadAt } : notification,
      ),
    );
    this.http.patch<void>(`${environment.apiBaseUrl}/notifications/read-all`, {}).subscribe({
      error: () => this.notificationsSignal.set(previous),
    });
  }
}
