import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { AuditEntry } from '../../core/models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

/**
 * audit.component.ts
 *
 * The audit trail: every sign-in, upload, verification run, decision and report
 * export the platform has recorded. The trail is readable by every role, and no
 * screen anywhere offers a way to change or delete an entry, which is what
 * makes it usable as compliance evidence.
 */
@Component({
  selector: 'cp-audit',
  standalone: true,
  imports: [DatePipe, StatusBadgeComponent],
  template: `
    <div class="page-heading">
      <h1>Audit trail</h1>
      <p>
        The 200 most recent recorded actions, newest first. Entries are written
        once and are never modified or removed.
      </p>
    </div>

    @if (error()) {
      <div class="alert error" role="alert">
        <strong>Could not load the audit trail</strong>
        {{ error() }}
      </div>
    }

    <div class="card">
      <div class="button-row" style="margin-bottom: 1rem;">
        <button type="button" class="secondary" (click)="downloadReport()">
          Download the audit report (PDF)
        </button>
      </div>

      <div class="table-wrap">
        <table>
          <caption>Recorded platform actions</caption>
          <thead>
            <tr>
              <th scope="col">When</th>
              <th scope="col">User</th>
              <th scope="col">Role</th>
              <th scope="col">Action</th>
              <th scope="col">Module</th>
              <th scope="col">Detail</th>
              <th scope="col">Outcome</th>
            </tr>
          </thead>
          <tbody>
            @for (entry of entries(); track entry.id) {
              <tr>
                <td>{{ entry.timestamp | date:'d MMM y, HH:mm:ss' }}</td>
                <td>{{ entry.username ?? '—' }}</td>
                <td>{{ entry.role ?? '—' }}</td>
                <th scope="row">{{ entry.action }}</th>
                <td>{{ entry.module ?? '—' }}</td>
                <td>{{ entry.detail ?? '—' }}</td>
                <td>
                  <cp-status-badge [label]="entry.outcome === 'SUCCESS' ? 'Success' : 'Failure'"
                                   [tone]="entry.outcome === 'SUCCESS' ? 'ok' : 'danger'" />
                </td>
              </tr>
            } @empty {
              <tr><td colspan="7">No actions have been recorded yet.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class AuditComponent {

  private readonly api = inject(ApiService);

  readonly entries = signal<AuditEntry[]>([]);
  readonly error = signal<string | null>(null);

  constructor() {
    this.api.auditTrail().subscribe({
      next: value => this.entries.set(value),
      error: () => this.error.set('The audit trail could not be retrieved from the server.')
    });
  }

  /** Downloads the audit trail as a PDF report. */
  downloadReport(): void {
    this.api.downloadReport('/api/reports/audit').subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'audit-log.pdf';
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.error.set('The audit report could not be generated.')
    });
  }
}
