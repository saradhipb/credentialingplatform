import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ProviderSummary } from '../../core/models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import {
  exclusionStatusLabel, exclusionStatusTone, npiStatusLabel, npiStatusTone,
  providerStatusLabel, providerStatusTone, recommendationLabel, recommendationTone
} from '../../shared/status';

/**
 * providers.component.ts
 *
 * The verification queue: every provider the platform has processed, with a
 * status filter and a free-text search across identifier and name.
 *
 * Filtering is performed on the server rather than in the browser so the screen
 * behaves the same however many providers are on file, and the result count is
 * announced in a live region so a screen-reader user knows how many rows a
 * filter produced.
 */
@Component({
  selector: 'cp-providers',
  standalone: true,
  imports: [FormsModule, DecimalPipe, RouterLink, StatusBadgeComponent],
  template: `
    <div class="page-heading">
      <h1>Provider queue</h1>
      <p>Every provider processed by the platform, most recent first.</p>
    </div>

    <div class="card">
      <form (ngSubmit)="load()" novalidate>
        <div class="grid cols-3">
          <div class="field">
            <label for="search">Search by name or NPI</label>
            <input id="search" name="search" type="search" [(ngModel)]="search"
                   placeholder="e.g. Harper or 1746805930">
          </div>
          <div class="field">
            <label for="status">Filter by status</label>
            <select id="status" name="status" [(ngModel)]="status">
              <option value="ALL">All statuses</option>
              <option value="ACTIVE">Verified</option>
              <option value="FLAGGED">Needs review</option>
              <option value="REJECTED">Not eligible</option>
              <option value="MANUAL_REVIEW">Manual review</option>
              <option value="PENDING">Pending</option>
            </select>
          </div>
          <div class="field" style="align-self: end;">
            <div class="button-row">
              <button type="submit">Apply</button>
              <button type="button" class="secondary" (click)="reset()">Clear</button>
            </div>
          </div>
        </div>
      </form>

      <p role="status" aria-live="polite" class="hint">
        {{ providers().length }} provider{{ providers().length === 1 ? '' : 's' }} shown.
      </p>
    </div>

    @if (error()) {
      <div class="alert error" role="alert">
        <strong>Could not load the queue</strong>
        {{ error() }}
      </div>
    }

    <div class="card">
      <div class="table-wrap">
        <table>
          <caption>Providers matching the current filter</caption>
          <thead>
            <tr>
              <th scope="col">Provider</th>
              <th scope="col">NPI</th>
              <th scope="col">Type</th>
              <th scope="col">Registry check</th>
              <th scope="col">Exclusion screen</th>
              <th scope="col" class="numeric">Risk</th>
              <th scope="col">Recommendation</th>
              <th scope="col">Status</th>
              <th scope="col"><span class="visually-hidden">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            @for (provider of providers(); track provider.id) {
              <tr>
                <th scope="row">{{ provider.displayName }}</th>
                <td class="mono">{{ provider.npi ?? '—' }}</td>
                <td>{{ provider.npiType === 'ORGANIZATION' ? 'Organization' : 'Individual' }}</td>
                <td><cp-status-badge [label]="npiLabel(provider.npiStatus)" [tone]="npiTone(provider.npiStatus)" /></td>
                <td><cp-status-badge [label]="oigLabel(provider.exclusionStatus)" [tone]="oigTone(provider.exclusionStatus)" /></td>
                <td class="numeric">{{ provider.riskScore === null ? '—' : (provider.riskScore | number:'1.1-1') }}</td>
                <td><cp-status-badge [label]="recLabel(provider.recommendation)" [tone]="recTone(provider.recommendation)" /></td>
                <td><cp-status-badge [label]="statusLabel(provider.status)" [tone]="statusTone(provider.status)" /></td>
                <td>
                  <a [routerLink]="['/providers', provider.id]">
                    Open<span class="visually-hidden"> the record for {{ provider.displayName }}</span>
                  </a>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="9">
                  No providers match the current filter.
                  <a routerLink="/upload">Upload a document</a> to add one.
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class ProvidersComponent {

  private readonly api = inject(ApiService);

  search = '';
  status = 'ALL';

  readonly providers = signal<ProviderSummary[]>([]);
  readonly error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  /** Reloads the queue using the current filter values. */
  load(): void {
    this.api.providers(this.status, this.search).subscribe({
      next: value => {
        this.providers.set(value);
        this.error.set(null);
      },
      error: () => this.error.set('The provider queue could not be retrieved from the server.')
    });
  }

  /** Clears the filters and reloads. */
  reset(): void {
    this.search = '';
    this.status = 'ALL';
    this.load();
  }

  npiLabel = npiStatusLabel;
  npiTone = npiStatusTone;
  oigLabel = exclusionStatusLabel;
  oigTone = exclusionStatusTone;
  recLabel = recommendationLabel;
  recTone = recommendationTone;
  statusLabel = providerStatusLabel;
  statusTone = providerStatusTone;
}
