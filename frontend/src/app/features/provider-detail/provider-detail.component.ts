import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ProviderDetail } from '../../core/models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { RiskMeterComponent } from '../../shared/risk-meter.component';
import {
  exclusionStatusLabel, exclusionStatusTone, npiStatusLabel, npiStatusTone,
  providerStatusLabel, providerStatusTone, recommendationLabel, recommendationTone, sourceLabel
} from '../../shared/status';

/**
 * provider-detail.component.ts
 *
 * The provider record: the identifying details on file, the evidence gathered
 * from the authoritative sources, the field-by-field comparison, the risk
 * assessment with its explanation, and the controls for recording a decision.
 *
 * The screen is organized so that evidence is presented before the score. That
 * ordering is intentional: a reviewer should read what the registry and the
 * exclusion list actually reported before being shown the platform's
 * suggestion, so that the suggestion informs the decision rather than anchoring
 * it.
 *
 * Read-only users see everything but no decision controls, matching the access
 * matrix the server enforces.
 */
@Component({
  selector: 'cp-provider-detail',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, PercentPipe, RouterLink,
            StatusBadgeComponent, RiskMeterComponent],
  template: `
    @if (error()) {
      <div class="alert error" role="alert">
        <strong>Could not load this provider</strong>
        {{ error() }}
        <p><a routerLink="/providers">Return to the provider queue</a></p>
      </div>
    }

    @if (provider(); as p) {
      <div class="page-heading">
        <h1>{{ p.displayName }}</h1>
        <p>
          {{ p.npiType === 'ORGANIZATION' ? 'Organizational provider (NPI Type 2)' : 'Individual provider (NPI Type 1)' }}
          &nbsp;&middot;&nbsp; Record created {{ p.createdAt | date:'d MMMM y, HH:mm' }}
        </p>
        <cp-status-badge [label]="statusLabel(p.status)" [tone]="statusTone(p.status)" />
      </div>

      @if (message()) {
        <div class="alert success" role="status">{{ message() }}</div>
      }

      <!-- ===== Details held on file ===================================== -->
      <div class="card">
        <h2>Details on file</h2>
        <dl class="detail">
          <dt>National Provider Identifier</dt>
          <dd class="mono">{{ p.npi ?? 'Not supplied' }}</dd>
          <dt>Practice address</dt>
          <dd>{{ addressOf(p) }}</dd>
          <dt>License number</dt>
          <dd>{{ p.licenseNumber ?? 'Not supplied' }}</dd>
          <dt>Licensing state</dt>
          <dd>{{ p.licenseState ?? 'Not supplied' }}</dd>
          <dt>Specialty</dt>
          <dd>{{ p.taxonomy ?? 'Not supplied' }}</dd>
        </dl>
      </div>

      <!-- ===== Evidence ================================================= -->
      <div class="card">
        <h2>Verification evidence</h2>

        @if (p.verification; as v) {
          <div class="grid cols-2">
            <div>
              <h3>CMS NPI registry</h3>
              <dl class="detail">
                <dt>Result</dt>
                <dd><cp-status-badge [label]="npiLabel(v.npiStatus)" [tone]="npiTone(v.npiStatus)" /></dd>
                <dt>Registry name</dt>
                <dd>{{ v.registryName ?? '—' }}</dd>
                <dt>Registry address</dt>
                <dd>{{ v.registryAddress ?? '—' }}</dd>
                <dt>Registry specialty</dt>
                <dd>{{ v.registryTaxonomy ?? '—' }}</dd>
                <dt>Enumeration date</dt>
                <dd>{{ v.enumerationDate ?? '—' }}</dd>
              </dl>
            </div>

            <div>
              <h3>OIG exclusion list</h3>
              <dl class="detail">
                <dt>Result</dt>
                <dd><cp-status-badge [label]="oigLabel(v.exclusionStatus)" [tone]="oigTone(v.exclusionStatus)" /></dd>
                <dt>Matched record</dt>
                <dd>{{ v.exclusionMatchedName ?? 'None' }}</dd>
                <dt>Exclusion date</dt>
                <dd>{{ v.exclusionDate ?? '—' }}</dd>
                <dt>Statutory basis</dt>
                <dd>{{ v.exclusionType ?? '—' }}</dd>
                <dt>Name similarity</dt>
                <dd>{{ v.exclusionMatchScore > 0 ? (v.exclusionMatchScore | percent:'1.0-0') : '—' }}</dd>
              </dl>
            </div>
          </div>

          <p class="hint">
            <strong>Evidence source:</strong> {{ srcLabel(v.source) }} &middot;
            checked {{ v.verifiedAt | date:'d MMMM y, HH:mm' }} &middot;
            external lookup took {{ v.durationMs }} ms.
          </p>

          @if (v.processingNote) {
            <div class="alert info" role="note">{{ v.processingNote }}</div>
          }

          <h3>Field comparison</h3>
          @if (v.discrepancies.length === 0) {
            <p class="hint">
              Every field published by both the document and the registry agreed.
              @if (v.missingFieldCount > 0) {
                {{ v.missingFieldCount }} expected field(s) were absent from the document.
              }
            </p>
          } @else {
            <div class="table-wrap">
              <table>
                <caption>Differences between the submitted document and the registry record</caption>
                <thead>
                  <tr>
                    <th scope="col">Field</th>
                    <th scope="col">Value on the document</th>
                    <th scope="col">Value in the registry</th>
                    <th scope="col">Significance</th>
                  </tr>
                </thead>
                <tbody>
                  @for (d of v.discrepancies; track d.field) {
                    <tr>
                      <th scope="row">{{ d.field }}</th>
                      <td>{{ d.documentValue }}</td>
                      <td>{{ d.registryValue }}</td>
                      <td>
                        <cp-status-badge
                          [label]="d.severity === 'HIGH' ? 'High' : d.severity === 'MEDIUM' ? 'Medium' : 'Low'"
                          [tone]="d.severity === 'HIGH' ? 'danger' : d.severity === 'MEDIUM' ? 'warn' : 'info'" />
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        } @else {
          <p class="hint">
            No verification has been completed for this provider. This happens
            when no usable details could be read from the submitted document.
          </p>
        }
      </div>

      <!-- ===== Risk assessment ========================================== -->
      <div class="card">
        <h2>Risk assessment</h2>

        @if (p.risk; as r) {
          <div class="grid cols-2">
            <div>
              <h3>Composite score</h3>
              <cp-risk-meter [score]="r.score" [recommendation]="r.recommendation" />
              <dl class="detail" style="margin-top: 1rem;">
                <dt>Platform recommendation</dt>
                <dd><cp-status-badge [label]="recLabel(r.recommendation)" [tone]="recTone(r.recommendation)" /></dd>
                <dt>Anomaly score</dt>
                <dd>{{ r.anomalyScore | number:'1.3-3' }} ({{ r.anomalous ? 'reported as anomalous' : 'within the normal range' }})</dd>
                <dt>Model confidence</dt>
                <dd>{{ r.confidence | percent:'1.0-0' }}</dd>
                <dt>Model version</dt>
                <dd class="mono">{{ r.modelVersion }}</dd>
                <dt>Assessed</dt>
                <dd>{{ r.assessedAt | date:'d MMMM y, HH:mm' }}</dd>
              </dl>
            </div>

            <div>
              <h3>Why this score</h3>
              @if (r.flags.length === 0) {
                <p class="hint">No adverse findings were recorded for this provider.</p>
              } @else {
                <ul class="flag-list">
                  @for (flag of r.flags; track flag) {
                    <li>{{ flag }}</li>
                  }
                </ul>
              }
              <details style="margin-top: 1rem;">
                <summary>Feature values scored by the model</summary>
                <p class="mono">{{ r.featureSnapshot }}</p>
                <p class="hint">
                  Each value runs from 0.000 (no concern) to 1.000 (maximum
                  concern). These are the inputs the anomaly detector saw.
                </p>
              </details>
            </div>
          </div>

          <!-- ===== Decision ============================================= -->
          <h3>Credentialing decision</h3>
          @if (r.reviewerDecision) {
            <div class="alert success" role="status">
              <strong>Decision recorded: {{ r.reviewerDecision }}</strong>
              Recorded by {{ r.reviewedBy }}.
            </div>
          } @else if (canEdit()) {
            <form (ngSubmit)="recordDecision(r.id)" novalidate>
              <div class="grid cols-2">
                <div class="field">
                  <label for="decision">Decision</label>
                  <select id="decision" name="decision" [(ngModel)]="decision">
                    <option value="APPROVED">Approve this provider</option>
                    <option value="ESCALATED">Escalate for further investigation</option>
                    <option value="DENIED">Deny this provider</option>
                  </select>
                </div>
                <div class="field">
                  <label for="note">Justification</label>
                  <input id="note" name="note" type="text" [(ngModel)]="note"
                         placeholder="Briefly state the basis for the decision">
                </div>
              </div>
              <button type="submit" [disabled]="busy()">Record decision</button>
              <p class="hint">
                The decision is stored alongside the platform's recommendation
                and written to the audit trail. It does not replace the
                recommendation.
              </p>
            </form>
          } @else {
            <p class="hint">
              Your role has read-only access, so decisions cannot be recorded
              from this account.
            </p>
          }
        } @else {
          <p class="hint">No risk assessment exists for this provider.</p>
        }
      </div>

      <!-- ===== Documents =============================================== -->
      <div class="card">
        <h2>Submitted documents</h2>
        <div class="table-wrap">
          <table>
            <caption>Documents received for this provider</caption>
            <thead>
              <tr>
                <th scope="col">File</th>
                <th scope="col">Type</th>
                <th scope="col">Read by</th>
                <th scope="col" class="numeric">Confidence</th>
                <th scope="col">Status</th>
                <th scope="col">Uploaded</th>
              </tr>
            </thead>
            <tbody>
              @for (doc of p.documents; track doc.id) {
                <tr>
                  <th scope="row">{{ doc.filename }}</th>
                  <td>{{ doc.documentType ?? '—' }}</td>
                  <td>{{ engineLabel(doc.engine) }}</td>
                  <td class="numeric">{{ doc.confidence | percent:'1.0-0' }}</td>
                  <td>{{ doc.purged ? 'Stored copy purged under the retention policy' : doc.status }}</td>
                  <td>{{ doc.uploadedAt | date:'d MMM y, HH:mm' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="6">No documents are attached to this provider.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <!-- ===== Actions ================================================= -->
      <div class="card">
        <h2>Actions</h2>
        <div class="button-row">
          @if (canEdit()) {
            <button type="button" (click)="reverify(p.id)" [disabled]="busy()">
              Re-run verification
            </button>
          }
          <button type="button" class="secondary"
                  (click)="download('/api/reports/verification/' + p.id, 'credential-verification-' + p.id + '.pdf')">
            Download verification report (PDF)
          </button>
          <button type="button" class="secondary"
                  (click)="download('/api/reports/risk/' + p.id, 'risk-analysis-' + p.id + '.pdf')">
            Download risk report (PDF)
          </button>
          <a routerLink="/providers"><button type="button" class="secondary">Back to the queue</button></a>
        </div>
      </div>
    }
  `
})
export class ProviderDetailComponent {

  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  readonly provider = signal<ProviderDetail | null>(null);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly busy = signal(false);
  readonly canEdit = this.auth.canEdit;

  decision = 'APPROVED';
  note = '';

  constructor() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.load(id);
  }

  /** Loads the provider record. */
  private load(id: number): void {
    this.api.provider(id).subscribe({
      next: value => this.provider.set(value),
      error: () => this.error.set('This provider record could not be retrieved.')
    });
  }

  /** Re-runs verification and scoring, then reloads the screen. */
  reverify(id: number): void {
    this.busy.set(true);
    this.api.reverify(id).subscribe({
      next: () => {
        this.busy.set(false);
        this.message.set('Verification was re-run against the external sources.');
        this.load(id);
      },
      error: () => {
        this.busy.set(false);
        this.error.set('The re-verification did not complete.');
      }
    });
  }

  /** Records the reviewer's decision against the current assessment. */
  recordDecision(assessmentId: number): void {
    this.busy.set(true);
    this.api.recordDecision(assessmentId, this.decision, this.note).subscribe({
      next: () => {
        this.busy.set(false);
        this.message.set('The decision has been recorded and written to the audit trail.');
        const current = this.provider();
        if (current) {
          this.load(current.id);
        }
      },
      error: () => {
        this.busy.set(false);
        this.error.set('The decision could not be recorded.');
      }
    });
  }

  /**
   * Downloads a report. The blob is fetched through the API service so the
   * session token is attached, then handed to the browser as a temporary
   * object URL, which is revoked immediately afterwards.
   *
   * @param path     the report endpoint
   * @param filename the name to save the file under
   */
  download(path: string, filename: string): void {
    this.api.downloadReport(path).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.error.set('The report could not be generated.')
    });
  }

  /** Builds a one-line address from the parts held on file. */
  addressOf(p: ProviderDetail): string {
    const parts = [p.addressLine1, p.city, p.state, p.postalCode].filter(Boolean);
    return parts.length > 0 ? parts.join(', ') : 'Not supplied';
  }

  /** Turns the extraction engine code into readable wording. */
  engineLabel(engine: string | null): string {
    switch (engine) {
      case 'PDF_TEXT_LAYER': return 'PDF text layer';
      case 'TESSERACT_OCR': return 'Optical character recognition';
      case 'PLAIN_TEXT': return 'Plain text';
      case 'NONE': return 'Could not be read';
      default: return engine ?? '—';
    }
  }

  npiLabel = npiStatusLabel;
  npiTone = npiStatusTone;
  oigLabel = exclusionStatusLabel;
  oigTone = exclusionStatusTone;
  recLabel = recommendationLabel;
  recTone = recommendationTone;
  statusLabel = providerStatusLabel;
  statusTone = providerStatusTone;
  srcLabel = sourceLabel;
}
