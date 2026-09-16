import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { LoginResponse } from './models';

/**
 * auth.service.ts
 *
 * Holds the signed-in session for the browser tab and exposes it as Angular
 * signals, so any component that reads `currentUser()` re-renders when the
 * session changes without needing its own subscription.
 *
 * The token is kept in `sessionStorage` rather than `localStorage` on purpose:
 * sessionStorage is cleared when the tab closes, so a session does not outlive
 * the browsing session on a shared workstation, which is the normal setting for
 * a credentialing office.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private static readonly STORAGE_KEY = 'credentialing.session';

  /** The signed-in user, or null when nobody is signed in. */
  private readonly session = signal<LoginResponse | null>(this.restore());

  /** Read-only view of the current session. */
  readonly currentUser = computed(() => this.session());

  /** True when a session is open. */
  readonly isSignedIn = computed(() => this.session() !== null);

  /** True when the signed-in user may upload documents and record decisions. */
  readonly canEdit = computed(() => {
    const role = this.session()?.role;
    return role === 'ADMIN' || role === 'REVIEWER';
  });

  /** True when the signed-in user may reach the administration screens. */
  readonly isAdmin = computed(() => this.session()?.role === 'ADMIN');

  constructor(private readonly http: HttpClient, private readonly router: Router) {}

  /**
   * Authenticates against the API and stores the returned session.
   *
   * @param username the account name
   * @param password the password
   * @returns an observable that completes once the session is stored
   */
  signIn(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { username, password })
      .pipe(tap(response => {
        this.session.set(response);
        sessionStorage.setItem(AuthService.STORAGE_KEY, JSON.stringify(response));
      }));
  }

  /**
   * Ends the session on the server and in the browser, then returns to the
   * sign-in screen. The local session is cleared even if the server call fails,
   * so a user can always get themselves signed out.
   */
  signOut(): void {
    this.http.post('/api/auth/logout', {}).subscribe({
      next: () => this.clearAndRedirect(),
      error: () => this.clearAndRedirect()
    });
  }

  /** @returns the bearer token for the active session, or null */
  token(): string | null {
    return this.session()?.token ?? null;
  }

  /** Clears the stored session without contacting the server. */
  clearSession(): void {
    this.session.set(null);
    sessionStorage.removeItem(AuthService.STORAGE_KEY);
  }

  private clearAndRedirect(): void {
    this.clearSession();
    void this.router.navigate(['/sign-in']);
  }

  /** Reloads a session stored earlier in this browser tab. */
  private restore(): LoginResponse | null {
    try {
      const raw = sessionStorage.getItem(AuthService.STORAGE_KEY);
      return raw ? (JSON.parse(raw) as LoginResponse) : null;
    } catch {
      return null;
    }
  }
}
