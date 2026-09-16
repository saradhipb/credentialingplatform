import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { PlatformUser, SystemHealth } from '../../core/models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

/**
 * admin.component.ts
 *
 * The Administrator Control Panel: integration health, model quality, accounts
 * and the manual retention purge.
 *
 * The integration panel exists so that an operator can tell whether the results
 * appearing elsewhere in the platform came from a live registry lookup or from
 * the bundled offline snapshot. That distinction changes how much weight a
 * verification result should carry, and it would otherwise be invisible.
 */
@Component({
  selector: 'cp-admin',
  standalone: true,
  imports: [DatePipe, DecimalPipe, PercentPipe, StatusBadgeComponent],
  template: `
    <div class="page-heading">
      <h1>Administrator control panel</h1>
      <p>Integration health, model quality, accounts and data retention.</p>
    </div>

    @if (error()) {
      <div class="alert error" role="alert">
        <strong>Could not load the control panel</strong>
        {{ error() }}
      </div>
    }

    @if (message()) {
      <div class="alert success" role="status">{{ message() }}</div>
    }

    @if (health(); as h) {
      <div class="grid cols-2">
        <div class="card">
          <h2>External sources</h2>
          <dl class="detail">
            <dt>Live CMS registry lookups</dt>
            <dd>
              <cp-status-badge [label]="h.nppesEnabled ? 'Enabled' : 'Disabled by configuration'"
                               [tone]="h.nppesEnabled ? 'ok' : 'warn'" />
            </dd>
            <dt>Registry reachable</dt>
            <dd>
              <cp-status-badge [label]="h.nppesReachable ? 'Reachable' : 'Unreachable'"
                               [tone]="h.nppesReachable ? 'ok' : 'danger'" />
            </dd>
            @if (h.nppesLastError) {
              <dt>Last registry error</dt>
              <dd class="mono">{{ h.nppesLastError }}</dd>
            }
            <dt>Offline snapshot</dt>
            <dd>{{ h.offlineSnapshotSize }} registry record(s) available as fallback</dd>
            <dt>OIG exclusion list</dt>
            <dd>
              <cp-status-badge [label]="h.exclusionListLoaded ? 'Loaded' : 'Not loaded'"
                               [tone]="h.exclusionListLoaded ? 'ok' : 'danger'" />
              &nbsp;{{ h.exclusionRecordCount }} record(s)
            </dd>
            <dt>Text extraction engines</dt>
            <dd>
              <ul class="flag-list">
                @for (engine of h.ocrEngines; track engine) {
                  <li>{{ engine }}</li>
                }
              </ul>
            </dd>
          </dl>
        </div>

        <div class="card">
          <h2>Anomaly detection model</h2>
          <dl class="detail">
            <dt>Model version</dt>
            <dd class="mono">{{ h.modelVersion }}</dd>
            <dt>Decision threshold</dt>
            <dd>{{ h.anomalyThreshold | number:'1.2-2' }}</dd>
            <dt>Precision</dt>
            <dd>{{ h.precision | percent:'1.1-1' }}</dd>
            <dt>Recall</dt>
            <dd>{{ h.recall | percent:'1.1-1' }}</dd>
            <dt>F1 score</dt>
            <dd>{{ h.f1Score | number:'1.3-3' }}</dd>
            <dt>Accuracy</dt>
            <dd>{{ h.accuracy | percent:'1.1-1' }}</dd>
            <dt>Evaluation set</dt>
            <dd>{{ h.evaluationSampleCount }} labelled records</dd>
          </dl>
          <p class="hint">
            Measured by the running platform against its labelled evaluation set
            each time the service starts. The Milestone 2 target is 0.85 for both
            precision and recall.
          </p>
        </div>
      </div>

      <div class="card">
        <h2>Document retention</h2>
        <dl class="detail">
          <dt>Storage location</dt>
          <dd class="mono">{{ h.uploadDirectory }}</dd>
          <dt>Retention window</dt>
          <dd>{{ h.retentionDays }} day(s)</dd>
        </dl>
        <p class="hint">
          Stored document files are deleted automatically once the retention
          window closes. The extracted fields, the verification evidence and the
          audit trail are kept; the copy of the provider's document is not.
        </p>
        <button type="button" (click)="purge()" [disabled]="busy()">
          Run the retention purge now
        </button>
      </div>
    }

    <div class="card">
      <h2>Accounts</h2>
      <div class="table-wrap">
        <table>
          <caption>Platform accounts and their roles</caption>
          <thead>
            <tr>
              <th scope="col">Username</th>
              <th scope="col">Name</th>
              <th scope="col">Email</th>
              <th scope="col">Role</th>
              <th scope="col">Enabled</th>
              <th scope="col">Last sign-in</th>
            </tr>
          </thead>
          <tbody>
            @for (user of users(); track user.id) {
              <tr>
                <th scope="row">{{ user.username }}</th>
                <td>{{ user.displayName ?? '—' }}</td>
                <td>{{ user.email ?? '—' }}</td>
                <td>{{ user.role }}</td>
                <td>
                  <cp-status-badge [label]="user.active ? 'Enabled' : 'Disabled'"
                                   [tone]="user.active ? 'ok' : 'warn'" />
                </td>
                <td>{{ user.lastLoginAt ? (user.lastLoginAt | date:'d MMM y, HH:mm') : 'Never' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="6">No accounts exist.</td></tr>
            }
          </tbody>
        </table>
      </div>
      <p class="hint">
        Passwords are never stored. Each account holds a per-user random salt and
        a PBKDF2-HMAC-SHA256 derived key.
      </p>
    </div>
  `
})
export class AdminComponent {

  private readonly api = inject(ApiService);

  readonly health = signal<SystemHealth | null>(null);
  readonly users = signal<PlatformUser[]>([]);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly busy = signal(false);

  constructor() {
    this.api.systemHealth().subscribe({
      next: value => this.health.set(value),
      error: () => this.error.set('System health could not be retrieved.')
    });
    this.api.users().subscribe({
      next: value => this.users.set(value),
      error: () => this.error.set('The account list could not be retrieved.')
    });
  }

  /** Runs the retention purge immediately. */
  purge(): void {
    this.busy.set(true);
    this.api.purgeExpiredDocuments().subscribe({
      next: result => {
        this.busy.set(false);
        this.message.set(`Retention purge complete. ${result.purgedDocuments} stored `
          + `document file(s) older than ${result.retentionDays} day(s) were removed.`);
      },
      error: () => {
        this.busy.set(false);
        this.error.set('The retention purge did not complete.');
      }
    });
  }
}
