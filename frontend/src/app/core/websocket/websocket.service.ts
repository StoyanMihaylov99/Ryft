import { InjectionToken, Injectable, effect, inject } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';
import { Observable, share } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Notification } from '../notification/models';
import { BoardUpdateMessage } from './models';

/** Builds the single `Client` this service owns. Injected rather than constructed inline so a test
 *  can substitute a fake `Client` and never open a real socket — see `websocket.service.spec.ts`. The
 *  default (real) factory is registered in `app.config.ts`. */
export const STOMP_CLIENT_FACTORY = new InjectionToken<() => Client>('STOMP_CLIENT_FACTORY');

/**
 * One STOMP connection for the whole app, connected for as long as the user is signed in. No page
 * calls `connect`/`disconnect` directly — inject this once in `app.ts` to keep the singleton alive
 * and reacting to `AuthService.isAuthenticated()` for the app's lifetime; every other consumer just
 * calls `watchProjectBoard`/`watchNotifications`.
 */
@Injectable({ providedIn: 'root' })
export class WebsocketService {
  private readonly authService = inject(AuthService);
  private readonly client = inject(STOMP_CLIENT_FACTORY)();

  /** Every currently-interested `watch<T>` subscriber, notified once per successful (re)connect so a
   *  destination subscribed to before the socket was up — or resubscribed after a drop — is issued a
   *  fresh STOMP `SUBSCRIBE`. */
  private readonly onConnectListeners = new Set<() => void>();

  constructor() {
    this.client.reconnectDelay = 5000;
    // Re-read on every (re)connect attempt, never cached at construction time — the access token is
    // in-memory only and can be refreshed at any point between connects.
    this.client.beforeConnect = () => {
      const token = this.authService.getAccessToken();
      this.client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
    };
    this.client.onConnect = () => {
      for (const listener of this.onConnectListeners) {
        listener();
      }
    };

    effect(() => {
      if (this.authService.isAuthenticated()) {
        this.client.activate();
      } else {
        this.client.deactivate();
      }
    });
  }

  watchProjectBoard(projectKey: string): Observable<BoardUpdateMessage> {
    return this.watch<BoardUpdateMessage>(`/topic/projects/${projectKey}/board`);
  }

  watchNotifications(): Observable<Notification> {
    return this.watch<Notification>('/user/queue/notifications');
  }

  /** Ref-counted per call: the first subscriber sends a STOMP `SUBSCRIBE`, the last unsubscribe
   *  sends `UNSUBSCRIBE` — `share({ resetOnRefCountZero: true })` tears the underlying STOMP
   *  subscription down rather than leaving it multicasting to zero listeners. */
  private watch<T>(destination: string): Observable<T> {
    return new Observable<T>((subscriber) => {
      let stompSubscription: StompSubscription | undefined;
      const subscribeIfConnected = (): void => {
        if (this.client.connected) {
          stompSubscription = this.client.subscribe(destination, (message) => {
            subscriber.next(JSON.parse(message.body) as T);
          });
        }
      };

      subscribeIfConnected();
      this.onConnectListeners.add(subscribeIfConnected);

      return () => {
        this.onConnectListeners.delete(subscribeIfConnected);
        stompSubscription?.unsubscribe();
      };
    }).pipe(share({ resetOnRefCountZero: true }));
  }
}
