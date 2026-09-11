import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

type Mode = 'login' | 'register';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-login',
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly mode = signal<Mode>('login');
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly loginForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  readonly registerForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    displayName: ['', [Validators.required]],
  });

  constructor() {
    const oauthError = this.route.snapshot.queryParamMap.get('error');
    if (oauthError) {
      this.errorMessage.set(this.describeOAuthError(oauthError));
    }
  }

  switchMode(mode: Mode): void {
    this.mode.set(mode);
    this.errorMessage.set(null);
  }

  submitLogin(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.authService.login(this.loginForm.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/workspace'),
      error: () => {
        this.submitting.set(false);
        this.errorMessage.set('Incorrect email or password.');
      },
    });
  }

  submitRegister(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.authService.register(this.registerForm.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/workspace'),
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(
          error.status === 409
            ? 'An account with that email already exists.'
            : 'Registration failed. Please try again.',
        );
      },
    });
  }

  private describeOAuthError(code: string): string {
    switch (code) {
      case 'oauth_email_conflict':
        return 'That email is already registered. Log in with your password first, then link this provider from your account settings.';
      case 'oauth_missing_email':
        return 'We could not get an email address from that provider.';
      case 'oauth_login_failed':
        return 'Sign-in was cancelled or failed. Please try again.';
      default:
        return 'Sign-in failed. Please try again.';
    }
  }
}
