import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

/**
 * app.component.ts
 *
 * The application shell: header, primary navigation, accessibility controls and
 * the routed view.
 *
 * Three accessibility features live here. A skip link, the first focusable
 * element on the page, lets a keyboard user jump straight to the content. A
 * high-contrast toggle strengthens borders and darkens text for low-vision use.
 * A larger-text toggle raises the root font size, which scales the whole layout
 * because every dimension in the stylesheet is expressed in relative units.
 * Both preferences persist in the browser between visits.
 */
@Component({
  selector: 'cp-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    @if (signedIn()) {
      <a class="skip-link" href="#main-content">Skip to the main content</a>

      <header class="app-header">
        <div class="product">
          Provider Credentialing Platform
          <small>AI-enhanced verification, exclusion screening and risk analysis</small>
        </div>

        <button type="button" class="small secondary" (click)="toggleContrast()"
                [attr.aria-pressed]="highContrast()">
          High contrast
        </button>
        <button type="button" class="small secondary" (click)="toggleTextSize()"
                [attr.aria-pressed]="largeText()">
          Larger text
        </button>

        <span class="who">
          {{ user()?.displayName }}
          <small>{{ roleLabel() }}</small>
        </span>

        <button type="button" class="small secondary" (click)="signOut()">Sign out</button>
      </header>

      <nav class="app-nav" aria-label="Primary">
        <a routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
        <a routerLink="/providers" routerLinkActive="active">Provider queue</a>
        <a routerLink="/review" routerLinkActive="active">Review queue</a>
        @if (canEdit()) {
          <a routerLink="/upload" routerLinkActive="active">Upload document</a>
        }
        <a routerLink="/audit" routerLinkActive="active">Audit trail</a>
        @if (isAdmin()) {
          <a routerLink="/admin" routerLinkActive="active">Administration</a>
        }
      </nav>

      <main id="main-content" tabindex="-1">
        <router-outlet />
      </main>
    } @else {
      <router-outlet />
    }
  `,
  styles: [`
    .who { text-align: right; line-height: 1.2; font-size: 0.9rem; }
    .who small { display: block; opacity: 0.85; font-size: 0.75rem; }
  `]
})
export class AppComponent {

  private static readonly CONTRAST_KEY = 'credentialing.highContrast';
  private static readonly TEXT_KEY = 'credentialing.largeText';

  private readonly auth = inject(AuthService);

  readonly user = this.auth.currentUser;
  readonly signedIn = this.auth.isSignedIn;
  readonly canEdit = this.auth.canEdit;
  readonly isAdmin = this.auth.isAdmin;

  readonly highContrast = signal(localStorage.getItem(AppComponent.CONTRAST_KEY) === '1');
  readonly largeText = signal(localStorage.getItem(AppComponent.TEXT_KEY) === '1');

  constructor() {
    this.applyPreferences();
  }

  /** Turns the high-contrast palette on or off and remembers the choice. */
  toggleContrast(): void {
    this.highContrast.set(!this.highContrast());
    localStorage.setItem(AppComponent.CONTRAST_KEY, this.highContrast() ? '1' : '0');
    this.applyPreferences();
  }

  /** Turns the larger-text mode on or off and remembers the choice. */
  toggleTextSize(): void {
    this.largeText.set(!this.largeText());
    localStorage.setItem(AppComponent.TEXT_KEY, this.largeText() ? '1' : '0');
    this.applyPreferences();
  }

  /** Ends the session. */
  signOut(): void {
    this.auth.signOut();
  }

  /** @returns the caller's role in the wording used in the interface */
  roleLabel(): string {
    switch (this.user()?.role) {
      case 'ADMIN': return 'Administrator';
      case 'REVIEWER': return 'Credentialing specialist';
      case 'VIEWER': return 'Read-only access';
      default: return '';
    }
  }

  /** Writes the current preferences onto the document body. */
  private applyPreferences(): void {
    document.body.classList.toggle('high-contrast', this.highContrast());
    document.body.classList.toggle('large-text', this.largeText());
  }
}
