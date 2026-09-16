import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

/**
 * login.component.ts
 *
 * The sign-in screen. Two accessibility behaviours are worth noting: the error
 * region is marked `role="alert"` so a failed sign-in is announced immediately
 * rather than silently appearing, and every field has a real `<label>` bound by
 * `for`, so clicking the label focuses the field and a screen reader announces
 * the field's purpose.
 *
 * The demonstration credentials are printed on the screen because this is a
 * capstone prototype seeded with sample accounts. That panel is removed in any
 * deployment holding real provider information.
 */
@Component({
  selector: 'cp-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="sign-in">
      <div class="panel">
        <h1>Provider Credentialing Platform</h1>
        <p class="tagline">
          Automated verification against the CMS NPI registry and the HHS OIG
          exclusion list, with risk analysis for credentialing review.
        </p>

        @if (expired()) {
          <div class="alert info" role="status">
            Your session ended after a period of inactivity. Please sign in again.
          </div>
        }

        @if (error()) {
          <div class="alert error" role="alert">
            <strong>Unable to sign in</strong>
            {{ error() }}
          </div>
        }

        <form (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label for="username">Username</label>
            <input id="username" name="username" type="text" autocomplete="username"
                   [(ngModel)]="username" required autofocus>
          </div>

          <div class="field">
            <label for="password">Password</label>
            <input id="password" name="password" type="password" autocomplete="current-password"
                   [(ngModel)]="password" required>
          </div>

          <button type="submit" [disabled]="busy()">
            {{ busy() ? 'Signing in...' : 'Sign in' }}
          </button>
        </form>

        <div class="demo-accounts">
          <h2>Demonstration accounts</h2>
          <p class="hint">
            This prototype is seeded with one account per role so that the
            access rules can be demonstrated.
          </p>
          <table>
            <caption class="visually-hidden">Demonstration accounts and their roles</caption>
            <thead>
              <tr><th scope="col">Username</th><th scope="col">Password</th><th scope="col">Role</th></tr>
            </thead>
            <tbody>
              <tr><td>admin</td><td>Admin#2026</td><td>Administrator</td></tr>
              <tr><td>reviewer</td><td>Review#2026</td><td>Credentialing specialist</td></tr>
              <tr><td>viewer</td><td>Viewer#2026</td><td>Compliance officer, read only</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </main>
  `,
  styles: [`
    .sign-in {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 2rem 1rem;
      background: linear-gradient(160deg, #0b4f88 0%, #073a65 100%);
    }
    .panel {
      background: #fff;
      border-radius: 12px;
      padding: 2rem;
      width: min(32rem, 100%);
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25);
    }
    .tagline { color: #4d5a6d; margin-top: 0; }
    button { width: 100%; }
    .demo-accounts { margin-top: 2rem; border-top: 1px solid #d3d9e3; padding-top: 1rem; }
    .demo-accounts h2 { font-size: 0.95rem; }
    .demo-accounts table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
    .demo-accounts th, .demo-accounts td { text-align: left; padding: 0.3rem 0.4rem; border-bottom: 1px solid #eceff4; }
  `]
})
export class LoginComponent {

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  username = '';
  password = '';

  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly expired = signal(this.route.snapshot.queryParamMap.get('expired') === '1');

  /** Submits the credentials and navigates on success. */
  submit(): void {
    if (!this.username.trim() || !this.password) {
      this.error.set('Enter both a username and a password.');
      return;
    }

    this.busy.set(true);
    this.error.set(null);

    this.auth.signIn(this.username.trim(), this.password).subscribe({
      next: () => {
        this.busy.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/dashboard';
        void this.router.navigateByUrl(returnUrl);
      },
      error: (response: { error?: { message?: string } }) => {
        this.busy.set(false);
        this.error.set(response.error?.message
          ?? 'The platform could not be reached. Check that the server is running.');
      }
    });
  }
}
