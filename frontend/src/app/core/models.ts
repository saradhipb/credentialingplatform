/**
 * models.ts
 *
 * TypeScript interfaces mirroring the JSON contract published by the Spring
 * Boot API. Declaring the shapes explicitly, rather than typing responses as
 * `any`, means the compiler catches a mismatch between client and server at
 * build time instead of leaving it to appear as an empty cell in the interface.
 */

/** Response to a successful sign-in. */
export interface LoginResponse {
  token: string;
  username: string;
  displayName: string;
  role: 'ADMIN' | 'REVIEWER' | 'VIEWER';
  sessionTimeoutMinutes: number;
}

/** One row of the verification queue. */
export interface ProviderSummary {
  id: number;
  npi: string | null;
  displayName: string;
  npiType: string;
  status: string;
  npiStatus: string | null;
  exclusionStatus: string | null;
  riskScore: number | null;
  recommendation: string | null;
  state: string | null;
  lastVerifiedAt: string | null;
}

/** A field-level difference between the document and the registry. */
export interface Discrepancy {
  field: string;
  documentValue: string;
  registryValue: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW';
}

/** Evidence gathered from the authoritative sources. */
export interface Verification {
  id: number;
  npiStatus: string;
  registryName: string | null;
  registryAddress: string | null;
  registryTaxonomy: string | null;
  enumerationDate: string | null;
  exclusionStatus: string;
  exclusionMatchedName: string | null;
  exclusionDate: string | null;
  exclusionType: string | null;
  exclusionMatchScore: number;
  discrepancies: Discrepancy[];
  missingFieldCount: number;
  source: string;
  processingNote: string | null;
  durationMs: number;
  verifiedAt: string;
}

/** Output of the intelligence layer for one verification event. */
export interface RiskAssessment {
  id: number;
  score: number;
  anomalyScore: number;
  anomalous: boolean;
  confidence: number;
  recommendation: string;
  flags: string[];
  featureSnapshot: string | null;
  modelVersion: string;
  reviewerDecision: string | null;
  reviewedBy: string | null;
  assessedAt: string;
}

/** Metadata about one uploaded credential document. */
export interface CredentialDocument {
  id: number;
  filename: string;
  documentType: string | null;
  status: string;
  engine: string | null;
  confidence: number;
  sizeBytes: number;
  uploadedBy: string | null;
  uploadedAt: string;
  textPreview: string | null;
  purged: boolean;
}

/** The full provider record shown on the detail screen. */
export interface ProviderDetail {
  id: number;
  npi: string | null;
  displayName: string;
  firstName: string | null;
  lastName: string | null;
  organizationName: string | null;
  npiType: string;
  addressLine1: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
  licenseNumber: string | null;
  licenseState: string | null;
  taxonomy: string | null;
  status: string;
  createdAt: string;
  documents: CredentialDocument[];
  verification: Verification | null;
  risk: RiskAssessment | null;
  riskHistory: RiskAssessment[];
}

/** The dashboard snapshot. */
export interface DashboardMetrics {
  totalProviders: number;
  verifiedProviders: number;
  flaggedProviders: number;
  rejectedProviders: number;
  manualReviewProviders: number;
  exclusionHits: number;
  possibleExclusionHits: number;
  npiNotFound: number;
  anomaliesDetected: number;
  averageRiskScore: number;
  averageVerificationMs: number;
  statusBreakdown: Record<string, number>;
  recommendationBreakdown: Record<string, number>;
  recentProviders: ProviderSummary[];
}

/** Result of processing one uploaded document. */
export interface UploadResult {
  providerId: number;
  documentId: number | null;
  displayName: string;
  npi: string | null;
  status: string;
  npiStatus: string | null;
  exclusionStatus: string | null;
  riskScore: number | null;
  recommendation: string | null;
  elapsedMs: number;
  warning: string | null;
}

/** One entry of the audit trail. */
export interface AuditEntry {
  id: number;
  timestamp: string;
  username: string | null;
  role: string | null;
  action: string;
  module: string | null;
  entityType: string | null;
  entityId: number | null;
  detail: string | null;
  outcome: string | null;
  ipAddress: string | null;
}

/** Integration and model health, shown on the administrator panel. */
export interface SystemHealth {
  nppesEnabled: boolean;
  nppesReachable: boolean;
  nppesLastError: string | null;
  offlineSnapshotSize: number;
  exclusionListLoaded: boolean;
  exclusionRecordCount: number;
  ocrEngines: string[];
  modelVersion: string;
  anomalyThreshold: number;
  precision: number;
  recall: number;
  f1Score: number;
  accuracy: number;
  evaluationSampleCount: number;
  uploadDirectory: string;
  retentionDays: number;
}

/** A platform account. */
export interface PlatformUser {
  id: number;
  username: string;
  displayName: string | null;
  email: string | null;
  role: string;
  active: boolean;
  lastLoginAt: string | null;
}
