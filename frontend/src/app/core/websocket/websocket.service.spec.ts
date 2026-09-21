import { WritableSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { AuthService } from '../auth/auth.service';
import { STOMP_CLIENT_FACTORY, WebsocketService } from './websocket.service';

/** A minimal stand-in for `@stomp/stompjs`'s `Client` — real enough for `WebsocketService` to drive
 *  (it only ever touches `reconnectDelay`/`connectHeaders`/`beforeConnect`/`onConnect`/`connected`/
 *  `activate`/`deactivate`/`subscribe`), without ever opening a real socket. */
class FakeClient {
  reconnectDelay = 0;
  connectHeaders: Record<string, string> = {};
  connected = false;
  beforeConnect: (() => void) | undefined;
  onConnect: (() => void) | undefined;
  activate = vi.fn();
  deactivate = vi.fn();
  subscribe = vi.fn(
    (_destination: string, _callback: (message: IMessage) => void): StompSubscription =>
      ({ id: 'sub', unsubscribe: vi.fn() }) as unknown as StompSubscription,
  );

  /** Simulates a successful (re)connect: runs `beforeConnect` (as the real client would, to collect
   *  fresh headers), flips `connected`, then fires `onConnect`. */
  simulateConnect(): void {
    this.beforeConnect?.();
    this.connected = true;
    this.onConnect?.();
  }
}

describe('WebsocketService', () => {
  let fakeClient: FakeClient;
  let isAuthenticated: WritableSignal<boolean>;
  let getAccessToken: () => string | null;

  beforeEach(() => {
    fakeClient = new FakeClient();
    isAuthenticated = signal(false);
    getAccessToken = () => 'token-123';

    TestBed.configureTestingModule({
      providers: [
        { provide: STOMP_CLIENT_FACTORY, useValue: () => fakeClient as unknown as Client },
        {
          provide: AuthService,
          useValue: {
            isAuthenticated,
            getAccessToken: () => getAccessToken(),
          },
        },
      ],
    });
  });

  it('does not activate the client until the user is authenticated', () => {
    TestBed.inject(WebsocketService);
    TestBed.tick();

    expect(fakeClient.activate).not.toHaveBeenCalled();
    expect(fakeClient.deactivate).toHaveBeenCalled();
  });

  it('activates the client once isAuthenticated becomes true', () => {
    TestBed.inject(WebsocketService);
    TestBed.tick();

    isAuthenticated.set(true);
    TestBed.tick();

    expect(fakeClient.activate).toHaveBeenCalledTimes(1);
  });

  it('deactivates the client once isAuthenticated becomes false again', () => {
    isAuthenticated.set(true);
    TestBed.inject(WebsocketService);
    TestBed.tick();
    expect(fakeClient.activate).toHaveBeenCalledTimes(1);

    isAuthenticated.set(false);
    TestBed.tick();

    expect(fakeClient.deactivate).toHaveBeenCalled();
  });

  it('reads a fresh access token into the Authorization connect header on every connect attempt', () => {
    TestBed.inject(WebsocketService);
    TestBed.tick();

    getAccessToken = () => 'first-token';
    fakeClient.beforeConnect?.();
    expect(fakeClient.connectHeaders).toEqual({ Authorization: 'Bearer first-token' });

    getAccessToken = () => 'refreshed-token';
    fakeClient.beforeConnect?.();
    expect(fakeClient.connectHeaders).toEqual({ Authorization: 'Bearer refreshed-token' });
  });

  it('sets reconnectDelay so a dropped connection recovers automatically', () => {
    TestBed.inject(WebsocketService);

    expect(fakeClient.reconnectDelay).toBeGreaterThan(0);
  });

  it('subscribes to the board topic once the connection is established, when subscribed before connecting', () => {
    const service = TestBed.inject(WebsocketService);
    TestBed.tick();

    const subscription = service.watchProjectBoard('TRK').subscribe();
    expect(fakeClient.subscribe).not.toHaveBeenCalled();

    fakeClient.simulateConnect();

    expect(fakeClient.subscribe).toHaveBeenCalledWith('/topic/projects/TRK/board', expect.any(Function));
    subscription.unsubscribe();
  });

  it('subscribes immediately when subscribing while already connected', () => {
    const service = TestBed.inject(WebsocketService);
    TestBed.tick();
    fakeClient.simulateConnect();
    fakeClient.subscribe.mockClear();

    const subscription = service.watchNotifications().subscribe();

    expect(fakeClient.subscribe).toHaveBeenCalledWith('/user/queue/notifications', expect.any(Function));
    subscription.unsubscribe();
  });

  it('tears down the STOMP subscription when the last observer unsubscribes', () => {
    const service = TestBed.inject(WebsocketService);
    TestBed.tick();
    fakeClient.simulateConnect();
    const stompUnsubscribe = vi.fn();
    fakeClient.subscribe.mockReturnValue({ id: 'sub-1', unsubscribe: stompUnsubscribe } as unknown as StompSubscription);

    const subscription = service.watchProjectBoard('TRK').subscribe();
    expect(stompUnsubscribe).not.toHaveBeenCalled();

    subscription.unsubscribe();

    expect(stompUnsubscribe).toHaveBeenCalled();
  });

  it('emits parsed message bodies to subscribers', () => {
    const service = TestBed.inject(WebsocketService);
    TestBed.tick();
    fakeClient.simulateConnect();

    let received: unknown;
    const subscription = service.watchProjectBoard('TRK').subscribe((message) => (received = message));

    const [, callback] = fakeClient.subscribe.mock.calls[0];
    callback({ body: JSON.stringify({ eventType: 'issue.created' }) } as IMessage);

    expect(received).toEqual({ eventType: 'issue.created' });
    subscription.unsubscribe();
  });
});
