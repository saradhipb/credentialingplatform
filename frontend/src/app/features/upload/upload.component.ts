import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { UploadResult } from '../../core/models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import {
  exclusionStatusLabel, exclusionStatusTone, npiStatusLabel, npiStatusTone,
  providerStatusLabel, providerStatusTone, recommendationLabel, recommendationTone
} from '../../shared/status';

/**
 * upload.component.ts
 *
 * The Document Upload screen. One submission runs the whole pipeline, so the
 * verification result appears on the same screen where the document was
 * submitted rather than the user having to go looking for it.
 *
 * The result region is a live region, so the outcome is announced when it
 * arrives, and the progress message is announced while processing runs. That
 * matters because a screen-reader user would otherwise have no indication that
 * anything was happening during the several seconds the pipeline takes.
 */
@Component({
  selector: 'cp-upload',
  standalone: true,
  imports: [FormsModule, DecimalPipe, RouterLink, StatusBadgeComponent],
  template: `
    <div class="page-heading">
      <h1>Upload a credential document</h1>
      <p>
        Submit a license, certification or identification record. The platform
        extracts the provider details, verifies them against the CMS NPI
        registry and the OIG exclusion list, and produces a risk assessment.
      </p>
    </div>

    <div class="grid cols-2">
      <div class="card">
        <h2>Document</h2>

        @if (error()) {
          <div class="alert error" role="alert">
            <strong>The document could not be processed</strong>
            {{ error() }}
          </div>
        }

        <form (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label for="documentType">Document type</label>
            <select id="documentType" name="documentType" [(ngModel)]="documentType">
              <option value="State license">State license</option>
              <option value="Board certification">Board certification</option>
              <option value="DEA registration">DEA registration</option>
              <option value="Primary source verification letter">Primary source verification letter</option>
              <option value="Identification record">Identification record</option>
              <option value="Other credential document">Other credential document</option>
            </select>
            <p class="hint">Used for filing only; it does not change how the document is read.</p>
          </div>

          <div class="field">
            <label for="file">Select a file</label>
            <input id="file" name="file" type="file" (change)="onFileSelected($event)"
                   accept=".pdf,.png,.jpg,.jpeg,.tif,.tiff,.bmp,.txt"
                   aria-describedby="file-help">
            <p class="hint" id="file-help">
              PDF, PNG, JPG, TIFF, BMP or TXT, up to 15 MB. Digital PDFs are read
              from their text layer; scans and images are read with optical
              character recognition.
            </p>
          </div>

          <button type="submit" [disabled]="busy() || !selectedFile">
            {{ busy() ? 'Processing...' : 'Upload and verify' }}
          </button>
        </form>

        <p role="status" aria-live="polite" class="hint">
          {{ busy() ? 'Extracting text, verifying against external sources and scoring the record. This can take a few seconds.' : '' }}
        </p>
      </div>

      <div class="card" aria-live="polite">
        <h2>Result</h2>

        @if (result(); as r) {
          @if (r.warning) {
            <div class="alert info" role="status">
              <strong>Processed with a limitation</strong>
              {{ r.warning }}
            </div>
          }

          <dl class="detail">
            <dt>Provider</dt>
            <dd>{{ r.displayName }}</dd>

            <dt>NPI read from document</dt>
            <dd class="mono">{{ r.npi ?? 'Not found in the document' }}</dd>

            <dt>Registry check</dt>
            <dd><cp-status-badge [label]="npiLabel(r.npiStatus)" [tone]="npiTone(r.npiStatus)" /></dd>

            <dt>Exclusion screen</dt>
            <dd><cp-status-badge [label]="oigLabel(r.exclusionStatus)" [tone]="oigTone(r.exclusionStatus)" /></dd>

            <dt>Composite risk score</dt>
            <dd>{{ r.riskScore === null ? 'Not assessed' : (r.riskScore | number:'1.1-1') + ' out of 100' }}</dd>

            <dt>Platform recommendation</dt>
            <dd><cp-status-badge [label]="recLabel(r.recommendation)" [tone]="recTone(r.recommendation)" /></dd>

            <dt>Workflow status</dt>
            <dd><cp-status-badge [label]="statusLabel(r.status)" [tone]="statusTone(r.status)" /></dd>

            <dt>Processing time</dt>
            <dd>{{ r.elapsedMs }} ms</dd>
          </dl>

          <div class="button-row" style="margin-top: 1rem;">
            <a class="button-link" [routerLink]="['/providers', r.providerId]">
              <button type="button">Open the full record</button>
            </a>
          </div>
        } @else {
          <p class="hint">
            No document has been processed in this session yet. Choose a file and
            select <strong>Upload and verify</strong> to see the result here.
          </p>
        }
      </div>
    </div>
  `,
  styles: [`.button-link { text-decoration: none; }`]
})
export class UploadComponent {

  private readonly api = inject(ApiService);

  documentType = 'State license';
  selectedFile: File | null = null;

  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<UploadResult | null>(null);

  /**
   * Records the file chosen in the file input.
   *
   * @param event the change event raised by the input element
   */
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files && input.files.length > 0 ? input.files[0] : null;
    this.error.set(null);
  }

  /** Uploads the selected file and displays the outcome. */
  submit(): void {
    if (!this.selectedFile) {
      this.error.set('Choose a file before selecting Upload and verify.');
      return;
    }

    this.busy.set(true);
    this.error.set(null);
    this.result.set(null);

    this.api.upload(this.selectedFile, this.documentType).subscribe({
      next: value => {
        this.busy.set(false);
        this.result.set(value);
      },
      error: (response: { error?: { message?: string } }) => {
        this.busy.set(false);
        this.error.set(response.error?.message
          ?? 'The upload did not complete. Check the file and try again.');
      }
    });
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
