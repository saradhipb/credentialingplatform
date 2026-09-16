import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AuditEntry, DashboardMetrics, PlatformUser, ProviderDetail,
  ProviderSummary, RiskAssessment, SystemHealth, UploadResult
} from './models';

/**
 * api.service.ts
 *
 * The single place in the client that knows API URLs. Components call methods
 * on this service and receive typed results, so a change to an endpoint is made
 * once here rather than in every screen that happens to use it.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {

  constructor(private readonly http: HttpClient) {}

  // ----- Dashboard --------------------------------------------------------

  /** @returns the aggregate figures shown on the Verification Dashboard */
  dashboardMetrics(): Observable<DashboardMetrics> {
    return this.http.get<DashboardMetrics>('/api/dashboard/metrics');
  }

  /** @returns assessments awaiting a human decision, highest risk first */
  reviewQueue(): Observable<ProviderSummary[]> {
    return this.http.get<ProviderSummary[]>('/api/dashboard/review-queue');
  }

  // ----- Providers --------------------------------------------------------

  /**
   * Lists providers for the verification queue.
   *
   * @param status optional workflow status filter
   * @param search optional free-text search across NPI and names
   */
  providers(status?: string, search?: string): Observable<ProviderSummary[]> {
    let params = new HttpParams();
    if (status && status !== 'ALL') {
      params = params.set('status', status);
    }
    if (search && search.trim().length > 0) {
      params = params.set('search', search.trim());
    }
    return this.http.get<ProviderSummary[]>('/api/providers', { params });
  }

  /** @param id the provider to load */
  provider(id: number): Observable<ProviderDetail> {
    return this.http.get<ProviderDetail>(`/api/providers/${id}`);
  }

  /** Re-runs verification and scoring for a provider already on file. */
  reverify(id: number): Observable<UploadResult> {
    return this.http.post<UploadResult>(`/api/providers/${id}/reverify`, {});
  }

  // ----- Document intake --------------------------------------------------

  /**
   * Uploads a credential document and runs the full pipeline.
   *
   * @param file         the selected file
   * @param documentType the classification chosen on the form
   */
  upload(file: File, documentType: string): Observable<UploadResult> {
    const form = new FormData();
    form.append('file', file);
    form.append('documentType', documentType);
    return this.http.post<UploadResult>('/api/documents/upload', form);
  }

  // ----- Risk -------------------------------------------------------------

  /** Records a reviewer's decision against an assessment. */
  recordDecision(assessmentId: number, decision: string, note: string): Observable<RiskAssessment> {
    return this.http.post<RiskAssessment>(`/api/risk/${assessmentId}/decision`, { decision, note });
  }

  // ----- Audit and administration ----------------------------------------

  /** @returns the most recent audit entries */
  auditTrail(): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>('/api/audit');
  }

  /** @returns integration and model health */
  systemHealth(): Observable<SystemHealth> {
    return this.http.get<SystemHealth>('/api/admin/health');
  }

  /** @returns every platform account */
  users(): Observable<PlatformUser[]> {
    return this.http.get<PlatformUser[]>('/api/admin/users');
  }

  /** Runs the document retention purge immediately. */
  purgeExpiredDocuments(): Observable<{ purgedDocuments: number; retentionDays: number }> {
    return this.http.post<{ purgedDocuments: number; retentionDays: number }>(
      '/api/admin/retention/purge', {});
  }

  // ----- Reports ----------------------------------------------------------

  /** Downloads a report as a binary blob for the browser to save. */
  downloadReport(path: string): Observable<Blob> {
    return this.http.get(path, { responseType: 'blob' });
  }
}
