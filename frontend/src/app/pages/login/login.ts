import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

type Mode = 'login' | 'register';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-login',
  styles: `
    :host {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      background: #f4f5f7;
      font-family:
        system-ui,
        -apple-system,
        sans-serif;
    }

    .card {
      width: 100%;
      max-width: 360px;
      padding: 2rem;
      border-radius: 8px;
      background: #fff;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
    }

    h1 {
      margin: 0 0 1.5rem;
      font-size: 1.375rem;
    }

    .tabs {
      display: flex;
      gap: 0.5rem;
      margin-bottom: 1.5rem;
    }

    .tabs button {
      flex: 1;
      padding: 0.5rem;
      border: 1px solid #dcdfe4;
      background: #fff;
      border-radius: 6px;
      cursor: pointer;
    }

    .tabs button.active {
      background: #0052cc;
      border-color: #0052cc;
      color: #fff;
    }

    form {
      display: flex;
      flex-direction: column;
      gap: 0.875rem;
    }

    label {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      font-size: 0.875rem;
      color: #42526e;
    }

    input {
      padding: 0.5rem 0.625rem;
      border: 1px solid #dcdfe4;
      border-radius: 4px;
      font-size: 0.9375rem;
    }

    .field-error {
      color: #de350b;
      font-size: 0.8125rem;
    }

    .submit {
      margin-top: 0.25rem;
      padding: 0.625rem;
      border: none;
      border-radius: 4px;
      background: #0052cc;
      color: #fff;
      font-size: 0.9375rem;
      cursor: pointer;
    }

    .submit:disabled {
      opacity: 0.6;
      cursor: default;
    }

    .banner {
      margin-bottom: 1rem;
      padding: 0.625rem 0.75rem;
      border-radius: 4px;
      background: #ffebe6;
      color: #bf2600;
      font-size: 0.875rem;
    }

    .oauth {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      margin-top: 1.25rem;
    }

    .oauth a {
      display: block;
      padding: 0.5rem;
      border: 1px solid #dcdfe4;
      border-radius: 4px;
      text-align: center;
      text-decoration: none;
      color: #172b4d;
      font-size: 0.9375rem;
    }

    .divider {
      margin: 1.25rem 0 0;
      text-align: center;
      color: #6b778c;
      font-size: 0.8125rem;
    }
  `,
  templateUrl: './login.html',
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
