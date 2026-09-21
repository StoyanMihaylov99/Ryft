import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './core/theme/theme.service';
import { WebsocketService } from './core/websocket/websocket.service';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
   private readonly themeService = inject(ThemeService);
   // Kept alive for the app's lifetime purely to keep this singleton connecting/disconnecting as
   // AuthService.isAuthenticated() changes — no page calls it directly (see WebsocketService's doc).
   private readonly websocketService = inject(WebsocketService);
}
