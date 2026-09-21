import { TestBed } from '@angular/core/testing';
import { Client } from '@stomp/stompjs';
import { AuthService } from './core/auth/auth.service';
import { STOMP_CLIENT_FACTORY } from './core/websocket/websocket.service';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        // App injects WebsocketService purely to keep it alive — these are its transitive
        // dependencies, faked so this test never opens a real socket or calls the real AuthService.
        {
          provide: STOMP_CLIENT_FACTORY,
          useValue: () => ({ activate: () => {}, deactivate: () => {} }) as unknown as Client,
        },
        { provide: AuthService, useValue: { isAuthenticated: () => false, getAccessToken: () => null } },
      ],
    })
      .compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

});
