import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ProviderSummary } from '../../core/models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import {
  exclusionStatusLabel, exclusionStatusTone, npiStatusLabel, npiStatusTone,
  recommendationLabel, recommendationTone
} from '../../shared/status';

/**
 * review-queue.component.ts
 *
 * The work queue: assessments on which no human decision has yet been recorded,
 * ordered by descending risk so the most consequential records are handled
 * first. This is the screen a credentialing specialist works from day to day.
 */
@Component({
  selector: 'cp-review-queue',
  standalone: true,
  imports: [DecimalPipe, RouterLink, StatusBadgeComponent],
  template: `
    <div class="page-heading">
      <h1>Review queue</h1>
      <p>
        Providers awaiting a credentialing decision, highest risk first. A record
        leaves this queue once a decision is recorded against it.
      </p>
    </div>

    @if (error()) {
      <div class="alert error" role="alert">
        <strong>Could not load the review queue</strong>
        {{ error() }}
      </div>
    }

    <div class="card">
      <p role="status" aria-live="polite" class="hint">
        {{ queue().length }} record{{ queue().length === 1 ? '' : 's' }} awaiting a decision.
      </p>

      <div class="table-wrap">
        <table>
          <caption>Assessments with no recorded decision</caption>
          <thead>
            <tr>
              <th scope="col">Provider</th>
              <th scope="col">NPI</th>
              <th scope="col" class="numeric">Risk</th>
              <th scope="col">Recommendation</th>
              <th scope="col">Registry check</th>
              <th scope="col">Exclusion screen</th>
              <th scope="col"><span class="visually-hidden">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            @for (provider of queue(); track provider.id) {
              <tr>
                <th scope="row">{{ provider.displayName }}</th>
                <td class="mono">{{ provider.npi ?? '—' }}</td>
                <td class="numeric">{{ provider.riskScore === null ? '—' : (provider.riskScore | number:'1.1-1') }}</td>
                <td><cp-status-badge [label]="recLabel(provider.recommendation)" [tone]="recTone(provider.recommendation)" /></td>
                <td><cp-status-badge [label]="npiLabel(provider.npiStatus)" [tone]="npiTone(provider.npiStatus)" /></td>
                <td><cp-status-badge [label]="oigLabel(provider.exclusionStatus)" [tone]="oigTone(provider.exclusionStatus)" /></td>
                <td>
                  <a [routerLink]="['/providers', provider.id]">
                    Review<span class="visually-hidden"> {{ provider.displayName }}</span>
                  </a>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="7">Nothing is waiting for a decision.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class ReviewQueueComponent {

  private readonly api = inject(ApiService);

  readonly queue = signal<ProviderSummary[]>([]);
  readonly error = signal<string | null>(null);

  constructor() {
    this.api.reviewQueue().subscribe({
      next: value => this.queue.set(value),
      error: () => this.error.set('The review queue could not be retrieved from the server.')
    });
  }

  npiLabel = npiStatusLabel;
  npiTone = npiStatusTone;
  oigLabel = exclusionStatusLabel;
  oigTone = exclusionStatusTone;
  recLabel = recommendationLabel;
  recTone = recommendationTone;
}
