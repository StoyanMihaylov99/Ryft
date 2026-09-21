import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { Client } from '@stomp/stompjs';
import { environment } from '../environments/environment';
import { authInterceptor } from './core/auth/auth.interceptor';
import { STOMP_CLIENT_FACTORY } from './core/websocket/websocket.service';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    { provide: STOMP_CLIENT_FACTORY, useValue: () => new Client({ brokerURL: environment.wsUrl }) },
  ]
};
