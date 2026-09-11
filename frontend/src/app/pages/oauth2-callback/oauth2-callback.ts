import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Landing page after a Google/GitHub redirect. The backend has already set the refresh cookie and
 * sent the browser here with no tokens in the URL — this page's only job is to trade that cookie for
 * an access token via the normal /auth/refresh flow, then continue into the app.
 */
@Component({
  imports: [],
  selector: 'app-oauth2-callback',
  templateUrl: './oauth2-callback.html',
  styleUrl: './oauth2-callback.css',
})
export class Oauth2Callback implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.authService.refresh().subscribe({
      next: () => this.router.navigateByUrl('/workspace'),
      error: () => this.router.navigateByUrl('/login?error=oauth'),
    });
  }
}
