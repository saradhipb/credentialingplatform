import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { DashboardMetrics } from '../../core/models';
import { KpiTileComponent } from '../../shared/kpi-tile.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import {
  exclusionStatusLabel, exclusionStatusTone, npiStatusLabel, npiStatusTone,
  providerStatusLabel, providerStatusTone, recommendationLabel, recommendationTone, riskTone
} from '../../shared/status';

/**
 * dashboard.component.ts
 *
 * The Verification Dashboard: the operational view a credentialing supervisor
 * opens first. It answers three questions in order - how much work is in the
 * system, what needs attention, and what happened most recently.
 *
 * The whole snapshot comes from a single API call so that the tiles and the
 * table can never disagree with one another.
 */
@Component({
  selector: 'cp-dashboard',
  standalone: true,
  imports: [DecimalPipe, RouterLink, KpiTileComponent, StatusBadgeComponent],
  template: `
    <div class="page-heading">
      <h1>Verification dashboard</h1>
      <p>
        Live status of provider credentialing across the platform. Figures
        refresh each time this screen is opened.
      </p>
    </div>

    @if (error()) {
      <div class="alert error" role="alert">
        <strong>Could not load the dashboard</strong>
        {{ error() }}
      </div>
    }

    @if (metrics(); as m) {
      <section aria-labelledby="volume-heading">
        <h2 id="volume-heading" class="visually-hidden">Processing volume</h2>
        <div class="grid kpis">
          <cp-kpi-tile [value]="m.totalProviders" label="Providers processed" tone="info" />
          <cp-kpi-tile [value]="m.verifiedProviders" label="Verified" tone="ok" />
          <cp-kpi-tile [value]="m.flaggedProviders" label="Awaiting review" tone="warn" />
          <cp-kpi-tile [value]="m.rejectedProviders" label="Not eligible" tone="danger" />
          <cp-kpi-tile [value]="m.manualReviewProviders" label="Manual review" tone="warn" />
        </div>
      </section>

      <section aria-labelledby="findings-heading" style="margin-top: 1rem;">
        <h2 id="findings-heading" class="visually-hidden">Compliance findings</h2>
        <div class="grid kpis">
          <cp-kpi-tile [value]="m.exclusionHits" label="OIG exclusions confirmed" tone="danger" />
          <cp-kpi-tile [value]="m.possibleExclusionHits" label="Possible name matches" tone="warn" />
          <cp-kpi-tile [value]="m.npiNotFound" label="NPIs not in registry" tone="warn" />
          <cp-kpi-tile [value]="m.anomaliesDetected" label="Anomalies flagged" tone="warn" />
          <cp-kpi-tile [value]="(m.averageRiskScore | number:'1.1-1') ?? '0'"
                       label="Average risk score" tone="info" />
        </div>
      </section>

      <div class="grid cols-2" style="margin-top: 1.25rem;">
        <div class="card">
          <h2>Outcome breakdown</h2>
          <table>
            <caption>Providers by workflow status</caption>
            <thead>
              <tr><th scope="col">Status</th><th scope="col" class="numeric">Providers</th></tr>
            </thead>
            <tbody>
              @for (entry of statusRows(m); track entry.key) {
                <tr>
                  <td><cp-status-badge [label]="statusLabel(entry.key)" [tone]="statusTone(entry.key)" /></td>
                  <td class="numeric">{{ entry.value }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <div class="card">
          <h2>Platform recommendations</h2>
          <table>
            <caption>Assessments by advisory recommendation</caption>
            <thead>
              <tr><th scope="col">Recommendation</th><th scope="col" class="numeric">Assessments</th></tr>
            </thead>
            <tbody>
              @for (entry of recommendationRows(m); track entry.key) {
                <tr>
                  <td><cp-status-badge [label]="recLabel(entry.key)" [tone]="recTone(entry.key)" /></td>
                  <td class="numeric">{{ entry.value }}</td>
                </tr>
              }
            </tbody>
          </table>
          <p class="hint">
            Mean external verification time:
            {{ m.averageVerificationMs | number:'1.0-0' }} ms.
          </p>
        </div>
      </div>

      <div class="card">
        <h2>Most recent activity</h2>
        @if (m.recentProviders.length === 0) {
          <p class="hint">
            No documents have been processed yet. Open
            <a routerLink="/upload">Upload document</a> to process one.
          </p>
        } @else {
          <div class="table-wrap">
            <table>
              <caption>The most recently processed providers</caption>
              <thead>
                <tr>
                  <th scope="col">Provider</th>
                  <th scope="col">NPI</th>
                  <th scope="col">Registry check</th>
                  <th scope="col">Exclusion screen</th>
                  <th scope="col" class="numeric">Risk</th>
                  <th scope="col">Status</th>
                  <th scope="col"><span class="visually-hidden">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                @for (provider of m.recentProviders; track provider.id) {
                  <tr>
                    <th scope="row">{{ provider.displayName }}</th>
                    <td class="mono">{{ provider.npi ?? '&mdash;' }}</td>
                    <td><cp-status-badge [label]="npiLabel(provider.npiStatus)"
                                         [tone]="npiTone(provider.npiStatus)" /></td>
                    <td><cp-status-badge [label]="oigLabel(provider.exclusionStatus)"
                                         [tone]="oigTone(provider.exclusionStatus)" /></td>
                    <td class="numeric">{{ provider.riskScore === null ? '—' : (provider.riskScore | number:'1.1-1') }}</td>
                    <td><cp-status-badge [label]="statusLabel(provider.status)"
                                         [tone]="statusTone(provider.status)" /></td>
                    <td><a [routerLink]="['/providers', provider.id]">Open<span class="visually-hidden"> {{ provider.displayName }}</span></a></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    } @else if (!error()) {
      <p role="status">Loading dashboard figures...</p>
    }
  `
})
export class DashboardComponent {

  private readonly api = inject(ApiService);

  readonly metrics = signal<DashboardMetrics | null>(null);
  readonly error = signal<string | null>(null);

  constructor() {
    this.api.dashboardMetrics().subscribe({
      next: value => this.metrics.set(value),
      error: () => this.error.set('The dashboard figures could not be retrieved from the server.')
    });
  }

  /** Turns the status map into rows the template can iterate. */
  statusRows(m: DashboardMetrics): { key: string; value: number }[] {
    return Object.entries(m.statusBreakdown).map(([key, value]) => ({ key, value }));
  }

  /** Turns the recommendation map into rows the template can iterate. */
  recommendationRows(m: DashboardMetrics): { key: string; value: number }[] {
    return Object.entries(m.recommendationBreakdown).map(([key, value]) => ({ key, value }));
  }

  statusLabel = providerStatusLabel;
  statusTone = providerStatusTone;
  npiLabel = npiStatusLabel;
  npiTone = npiStatusTone;
  oigLabel = exclusionStatusLabel;
  oigTone = exclusionStatusTone;
  recLabel = recommendationLabel;
  recTone = recommendationTone;
  riskTone = riskTone;
}
