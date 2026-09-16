/**
 * ui-preview-server.js
 *
 * A documentation and front-end development aid. It serves the compiled Angular
 * bundle and answers the platform's REST endpoints with a fixed set of records,
 * so that the interface can be opened, reviewed and screenshotted without the
 * Java service running.
 *
 * THIS IS NOT PART OF THE PRODUCT. It performs no verification, contacts no
 * registry and stores nothing. Every value it returns was produced by running
 * the platform's own scoring logic over the bundled sample documents (see
 * docs/scenario-results.txt), so the screens it renders match what the Spring
 * Boot service produces for the same inputs.
 *
 * Usage:  node tools/ui-preview-server.js            (serves on port 8080)
 *
 * It has no dependencies beyond the Node standard library.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 8080;
const DIST = path.join(__dirname, '..', 'frontend', 'dist');

/* ---------------------------------------------------------------------------
 * Fixed records, taken from the scoring run over the bundled sample documents.
 * ------------------------------------------------------------------------- */

const providers = [
  {
    id: 1, npi: '1746805930', displayName: 'Harper, Dana R', firstName: 'Dana R', lastName: 'Harper',
    organizationName: null, npiType: 'INDIVIDUAL', addressLine1: '4410 N Central Ave Ste 210',
    city: 'Phoenix', state: 'AZ', postalCode: '85012', licenseNumber: 'A12345', licenseState: 'AZ',
    taxonomy: 'Family Medicine', status: 'ACTIVE', createdAt: '2026-09-12T15:04:11Z',
    verification: {
      id: 1, npiStatus: 'VALID', registryName: 'HARPER, DANA', registryAddress: '4410 N CENTRAL AVE STE 210, PHOENIX, AZ 850121847',
      registryTaxonomy: 'Family Medicine', enumerationDate: '2009-03-17', exclusionStatus: 'NOT_EXCLUDED',
      exclusionMatchedName: null, exclusionDate: null, exclusionType: null, exclusionMatchScore: 0,
      discrepancies: [], missingFieldCount: 0, source: 'LIVE_API',
      processingNote: 'No identifier or name match found on the exclusion list.',
      durationMs: 384, verifiedAt: '2026-09-12T15:04:12Z'
    },
    risk: {
      id: 1, score: 7.4, anomalyScore: 0.495, anomalous: false, confidence: 0.26, recommendation: 'APPROVE',
      flags: [], featureSnapshot: '{"NPI verification risk": 0.000, "OIG exclusion risk": 0.000, "Discrepancy volume": 0.000, "Discrepancy severity": 0.000, "Missing field ratio": 0.000, "Extraction uncertainty": 0.030, "Name similarity gap": 0.031}',
      modelVersion: 'isolation-forest-120t-128s-seed42', reviewerDecision: null, reviewedBy: null,
      assessedAt: '2026-09-12T15:04:12Z'
    },
    documents: [{ id: 1, filename: '10-verified-individual-digital.pdf', documentType: 'Primary source verification letter',
      status: 'PARSED', engine: 'PDF_TEXT_LAYER', confidence: 0.97, sizeBytes: 2043, uploadedBy: 'reviewer',
      uploadedAt: '2026-09-12T15:04:11Z', textPreview: 'ARIZONA MEDICAL BOARD PRIMARY SOURCE VERIFICATION LETTER Provider Name: Harper, Dana R., M.D. ...', purged: false }]
  },
  {
    id: 2, npi: '1458293762', displayName: 'Whitfield, Reginald T', firstName: 'Reginald T', lastName: 'Whitfield',
    organizationName: null, npiType: 'INDIVIDUAL', addressLine1: '2810 E Speedway Blvd',
    city: 'Tucson', state: 'AZ', postalCode: '85716', licenseNumber: 'D55219', licenseState: 'AZ',
    taxonomy: 'Cardiovascular Disease', status: 'REJECTED', createdAt: '2026-09-12T15:06:40Z',
    verification: {
      id: 2, npiStatus: 'VALID', registryName: 'WHITFIELD, REGINALD',
      registryAddress: '2810 E SPEEDWAY BLVD, TUCSON, AZ 857161104', registryTaxonomy: 'Cardiovascular Disease',
      enumerationDate: '2007-11-09', exclusionStatus: 'EXCLUDED', exclusionMatchedName: 'WHITFIELD, REGINALD',
      exclusionDate: '20220715', exclusionType: '1128b4', exclusionMatchScore: 1.0,
      discrepancies: [], missingFieldCount: 0, source: 'LIVE_API',
      processingNote: 'NPI matches an active OIG exclusion record.', durationMs: 351, verifiedAt: '2026-09-12T15:06:41Z'
    },
    risk: {
      id: 2, score: 47.6, anomalyScore: 0.506, anomalous: false, confidence: 0.23, recommendation: 'REJECT',
      flags: ['Confirmed OIG exclusion matched by NPI'],
      featureSnapshot: '{"NPI verification risk": 0.000, "OIG exclusion risk": 1.000, "Discrepancy volume": 0.000, "Discrepancy severity": 0.000, "Missing field ratio": 0.000, "Extraction uncertainty": 0.000, "Name similarity gap": 0.042}',
      modelVersion: 'isolation-forest-120t-128s-seed42', reviewerDecision: null, reviewedBy: null,
      assessedAt: '2026-09-12T15:06:41Z'
    },
    documents: [{ id: 2, filename: '02-excluded-provider.txt', documentType: 'Credential summary', status: 'PARSED',
      engine: 'PLAIN_TEXT', confidence: 1.0, sizeBytes: 294, uploadedBy: 'reviewer',
      uploadedAt: '2026-09-12T15:06:40Z', textPreview: 'STATE LICENSING BOARD CREDENTIAL SUMMARY Provider Name: Whitfield, Reginald T., M.D. ...', purged: false }]
  },
  {
    id: 3, npi: '1283947566', displayName: 'Okafor, Chidi', firstName: 'Chidi', lastName: 'Okafor',
    organizationName: null, npiType: 'INDIVIDUAL', addressLine1: '4455 E Camelback Rd Suite 300',
    city: 'Scottsdale', state: 'AZ', postalCode: '85018', licenseNumber: 'B77120', licenseState: 'AZ',
    taxonomy: 'Internal Medicine', status: 'FLAGGED', createdAt: '2026-09-12T15:09:02Z',
    verification: {
      id: 3, npiStatus: 'VALID', registryName: 'OKAFOR, CHIDI',
      registryAddress: '1930 S DOBSON RD, MESA, AZ 852024410', registryTaxonomy: 'Internal Medicine',
      enumerationDate: '2012-08-02', exclusionStatus: 'NOT_EXCLUDED', exclusionMatchedName: null,
      exclusionDate: null, exclusionType: null, exclusionMatchScore: 0,
      discrepancies: [
        { field: 'Practice address', documentValue: '4455 E Camelback Rd Suite 300', registryValue: '1930 S DOBSON RD', severity: 'MEDIUM' },
        { field: 'City', documentValue: 'Scottsdale', registryValue: 'MESA', severity: 'MEDIUM' },
        { field: 'Postal code', documentValue: '85018', registryValue: '852024410', severity: 'LOW' }
      ],
      missingFieldCount: 0, source: 'LIVE_API',
      processingNote: 'No identifier or name match found on the exclusion list.',
      durationMs: 402, verifiedAt: '2026-09-12T15:09:03Z'
    },
    risk: {
      id: 3, score: 21.0, anomalyScore: 0.697, anomalous: true, confidence: 0.24, recommendation: 'REVIEW',
      flags: ['3 fields differ from the registry record',
              'Anomaly detector score 0.70. Flagged primarily on: Discrepancy volume; Discrepancy severity.'],
      featureSnapshot: '{"NPI verification risk": 0.000, "OIG exclusion risk": 0.000, "Discrepancy volume": 0.500, "Discrepancy severity": 0.500, "Missing field ratio": 0.000, "Extraction uncertainty": 0.000, "Name similarity gap": 0.000}',
      modelVersion: 'isolation-forest-120t-128s-seed42', reviewerDecision: null, reviewedBy: null,
      assessedAt: '2026-09-12T15:09:03Z'
    },
    documents: [{ id: 3, filename: '03-address-discrepancy.txt', documentType: 'Other credential document',
      status: 'PARSED', engine: 'PLAIN_TEXT', confidence: 1.0, sizeBytes: 428, uploadedBy: 'reviewer',
      uploadedAt: '2026-09-12T15:09:02Z', textPreview: 'CREDENTIALING APPLICATION - ATTACHMENT B Provider Name: Okafor, Chidi, M.D. ...', purged: false }]
  },
  {
    id: 4, npi: '1234567890', displayName: 'Sandoval, Marisol', firstName: 'Marisol', lastName: 'Sandoval',
    organizationName: null, npiType: 'INDIVIDUAL', addressLine1: '3010 W Charleston Blvd', city: 'Las Vegas',
    state: 'NV', postalCode: '89102', licenseNumber: 'H41827', licenseState: 'NV',
    taxonomy: 'Obstetrics and Gynecology', status: 'REJECTED', createdAt: '2026-09-12T15:11:18Z',
    verification: {
      id: 4, npiStatus: 'INVALID_FORMAT', registryName: null, registryAddress: null, registryTaxonomy: null,
      enumerationDate: null, exclusionStatus: 'NOT_EXCLUDED', exclusionMatchedName: null, exclusionDate: null,
      exclusionType: null, exclusionMatchScore: 0, discrepancies: [], missingFieldCount: 5, source: 'NONE',
      processingNote: 'The identifier failed the CMS check-digit test, so no registry lookup was made. No identifier or name match found on the exclusion list.',
      durationMs: 4, verifiedAt: '2026-09-12T15:11:18Z'
    },
    risk: {
      id: 4, score: 39.2, anomalyScore: 0.658, anomalous: true, confidence: 0.15, recommendation: 'REJECT',
      flags: ['Identifier failed the CMS check-digit test', '5 expected fields were absent from the document',
              'Anomaly detector score 0.66. Flagged primarily on: NPI verification risk; Missing field ratio; Name similarity gap.'],
      featureSnapshot: '{"NPI verification risk": 1.000, "OIG exclusion risk": 0.000, "Discrepancy volume": 0.000, "Discrepancy severity": 0.000, "Missing field ratio": 0.714, "Extraction uncertainty": 0.000, "Name similarity gap": 0.500}',
      modelVersion: 'isolation-forest-120t-128s-seed42', reviewerDecision: null, reviewedBy: null,
      assessedAt: '2026-09-12T15:11:18Z'
    },
    documents: [{ id: 4, filename: '04-invalid-npi.txt', documentType: 'Other credential document', status: 'PARSED',
      engine: 'PLAIN_TEXT', confidence: 1.0, sizeBytes: 242, uploadedBy: 'reviewer',
      uploadedAt: '2026-09-12T15:11:18Z', textPreview: 'PROVIDER ENROLMENT FORM Provider Name: Sandoval, Marisol, D.O. ...', purged: false }]
  },
  {
    id: 5, npi: null, displayName: 'Ferreira, Joaquim', firstName: 'Joaquim', lastName: 'Ferreira',
    organizationName: null, npiType: 'INDIVIDUAL', addressLine1: '1220 Ocean Park Blvd', city: 'Santa Monica',
    state: 'CA', postalCode: '90405', licenseNumber: 'K90233', licenseState: 'CA',
    taxonomy: 'Respiratory Therapy', status: 'FLAGGED', createdAt: '2026-09-12T15:13:50Z',
    verification: {
      id: 5, npiStatus: 'MISSING', registryName: null, registryAddress: null, registryTaxonomy: null,
      enumerationDate: null, exclusionStatus: 'NOT_EXCLUDED', exclusionMatchedName: null, exclusionDate: null,
      exclusionType: null, exclusionMatchScore: 0, discrepancies: [], missingFieldCount: 5, source: 'NONE',
      processingNote: 'No NPI was present in the document. No identifier or name match found on the exclusion list.',
      durationMs: 3, verifiedAt: '2026-09-12T15:13:50Z'
    },
    risk: {
      id: 5, score: 37.9, anomalyScore: 0.658, anomalous: true, confidence: 0.15, recommendation: 'REVIEW',
      flags: ['No NPI could be read from the submitted document', '5 expected fields were absent from the document',
              'Anomaly detector score 0.66. Flagged primarily on: NPI verification risk; Missing field ratio; Name similarity gap.'],
      featureSnapshot: '{"NPI verification risk": 1.000, "OIG exclusion risk": 0.000, "Discrepancy volume": 0.000, "Discrepancy severity": 0.000, "Missing field ratio": 0.714, "Extraction uncertainty": 0.000, "Name similarity gap": 0.500}',
      modelVersion: 'isolation-forest-120t-128s-seed42', reviewerDecision: null, reviewedBy: null,
      assessedAt: '2026-09-12T15:13:50Z'
    },
    documents: [{ id: 5, filename: '05-missing-npi.txt', documentType: 'Other credential document', status: 'PARSED',
      engine: 'PLAIN_TEXT', confidence: 1.0, sizeBytes: 329, uploadedBy: 'reviewer',
      uploadedAt: '2026-09-12T15:13:50Z', textPreview: 'FACILITY CREDENTIAL ATTESTATION Provider Name: Ferreira, Joaquim ...', purged: false }]
  },
  {
    id: 6, npi: '1192837460', displayName: 'Alvarado, Beatriz', firstName: 'Beatriz', lastName: 'Alvarado',
    organizationName: null, npiType: 'INDIVIDUAL', addressLine1: '1875 W Chandler Blvd', city: 'Chandler',
    state: 'AZ', postalCode: '85224', licenseNumber: 'NP-44018', licenseState: 'AZ',
    taxonomy: 'Nurse Practitioner - Family', status: 'FLAGGED', createdAt: '2026-09-12T15:15:27Z',
    verification: {
      id: 6, npiStatus: 'DEACTIVATED', registryName: 'ALVARADO, BEATRIZ',
      registryAddress: '1875 W CHANDLER BLVD, CHANDLER, AZ 852241207', registryTaxonomy: 'Nurse Practitioner - Family',
      enumerationDate: '2010-02-11', exclusionStatus: 'NOT_EXCLUDED', exclusionMatchedName: null,
      exclusionDate: null, exclusionType: null, exclusionMatchScore: 0, discrepancies: [], missingFieldCount: 0,
      source: 'LIVE_API', processingNote: 'No identifier or name match found on the exclusion list.',
      durationMs: 297, verifiedAt: '2026-09-12T15:15:28Z'
    },
    risk: {
      id: 6, score: 30.1, anomalyScore: 0.671, anomalous: true, confidence: 0.18, recommendation: 'REVIEW',
      flags: ['NPI is present in the registry but deactivated',
              'Anomaly detector score 0.67. Flagged primarily on: NPI verification risk.'],
      featureSnapshot: '{"NPI verification risk": 0.550, "OIG exclusion risk": 0.000, "Discrepancy volume": 0.000, "Discrepancy severity": 0.000, "Missing field ratio": 0.000, "Extraction uncertainty": 0.000, "Name similarity gap": 0.000}',
      modelVersion: 'isolation-forest-120t-128s-seed42', reviewerDecision: null, reviewedBy: null,
      assessedAt: '2026-09-12T15:15:28Z'
    },
    documents: [{ id: 6, filename: '06-deactivated-npi.txt', documentType: 'State license', status: 'PARSED',
      engine: 'PLAIN_TEXT', confidence: 1.0, sizeBytes: 249, uploadedBy: 'reviewer',
      uploadedAt: '2026-09-12T15:15:27Z', textPreview: 'NURSE PRACTITIONER CREDENTIAL RECORD Provider Name: Alvarado, Beatriz, N.P. ...', purged: false }]
  },
  {
    id: 7, npi: '2103948578', displayName: 'Saguaro Valley Family Clinic LLC', firstName: null, lastName: null,
    organizationName: 'Saguaro Valley Family Clinic LLC', npiType: 'ORGANIZATION',
    addressLine1: '5902 W Northern Ave', city: 'Glendale', state: 'AZ', postalCode: '85301',
    licenseNumber: 'CL-88431', licenseState: 'AZ', taxonomy: 'Clinic/Center - Primary Care',
    status: 'ACTIVE', createdAt: '2026-09-12T15:17:44Z',
    verification: {
      id: 7, npiStatus: 'VALID', registryName: 'SAGUARO VALLEY FAMILY CLINIC LLC',
      registryAddress: '5902 W NORTHERN AVE, GLENDALE, AZ 853013320', registryTaxonomy: 'Clinic/Center - Primary Care',
      enumerationDate: '2011-06-14', exclusionStatus: 'NOT_EXCLUDED', exclusionMatchedName: null,
      exclusionDate: null, exclusionType: null, exclusionMatchScore: 0, discrepancies: [], missingFieldCount: 0,
      source: 'LIVE_API', processingNote: 'No identifier or name match found on the exclusion list.',
      durationMs: 366, verifiedAt: '2026-09-12T15:17:45Z'
    },
    risk: {
      id: 7, score: 8.4, anomalyScore: 0.560, anomalous: false, confidence: 0.10, recommendation: 'APPROVE',
      flags: [],
      featureSnapshot: '{"NPI verification risk": 0.000, "OIG exclusion risk": 0.000, "Discrepancy volume": 0.000, "Discrepancy severity": 0.000, "Missing field ratio": 0.000, "Extraction uncertainty": 0.000, "Name similarity gap": 0.000}',
      modelVersion: 'isolation-forest-120t-128s-seed42', reviewerDecision: 'APPROVED', reviewedBy: 'reviewer',
      assessedAt: '2026-09-12T15:17:45Z'
    },
    documents: [{ id: 7, filename: '11-organization-digital.pdf', documentType: 'Other credential document',
      status: 'PARSED', engine: 'PDF_TEXT_LAYER', confidence: 0.97, sizeBytes: 1878, uploadedBy: 'reviewer',
      uploadedAt: '2026-09-12T15:17:44Z', textPreview: 'GROUP PRACTICE ENROLMENT PACKET Organization Name: Saguaro Valley Family Clinic LLC ...', purged: false }]
  },
  {
    id: 8, npi: '1234567893', displayName: 'Calloway, Marcus D', firstName: 'Marcus D', lastName: 'Calloway',
    organizationName: null, npiType: 'INDIVIDUAL', addressLine1: '118 W Adams St', city: 'Phoenix',
    state: 'AZ', postalCode: '85003', licenseNumber: 'P33418', licenseState: 'AZ',
    taxonomy: 'Internal Medicine', status: 'FLAGGED', createdAt: '2026-09-12T15:19:09Z',
    verification: {
      id: 8, npiStatus: 'NOT_FOUND', registryName: null, registryAddress: null, registryTaxonomy: null,
      enumerationDate: null, exclusionStatus: 'POSSIBLE_MATCH', exclusionMatchedName: 'CALLOWAY, MARCUS',
      exclusionDate: '20190228', exclusionType: '1128a1', exclusionMatchScore: 0.9765,
      discrepancies: [], missingFieldCount: 0, source: 'LIVE_API',
      processingNote: 'The registry responded but holds no record for this identifier. Name is 98% similar to an excluded party; human confirmation required.',
      durationMs: 331, verifiedAt: '2026-09-12T15:19:10Z'
    },
    risk: {
      id: 8, score: 60.2, anomalyScore: 0.646, anomalous: true, confidence: 0.12, recommendation: 'REVIEW',
      flags: ['Possible OIG name match (98% similarity) requires confirmation', 'NPI not found in the CMS registry',
              'Anomaly detector score 0.65. Flagged primarily on: NPI verification risk; OIG exclusion risk; Name similarity gap.'],
      featureSnapshot: '{"NPI verification risk": 0.850, "OIG exclusion risk": 0.750, "Discrepancy volume": 0.000, "Discrepancy severity": 0.000, "Missing field ratio": 0.000, "Extraction uncertainty": 0.000, "Name similarity gap": 0.500}',
      modelVersion: 'isolation-forest-120t-128s-seed42', reviewerDecision: null, reviewedBy: null,
      assessedAt: '2026-09-12T15:19:10Z'
    },
    documents: [{ id: 8, filename: '08-possible-exclusion-name-match.txt', documentType: 'Other credential document',
      status: 'PARSED', engine: 'PLAIN_TEXT', confidence: 1.0, sizeBytes: 228, uploadedBy: 'reviewer',
      uploadedAt: '2026-09-12T15:19:09Z', textPreview: 'CREDENTIALING INTAKE FORM Provider Name: Calloway, Marcus D., M.D. ...', purged: false }]
  },
  {
    id: 9, npi: '1637482955', displayName: 'Nakamura, Kenji', firstName: 'Kenji', lastName: 'Nakamura',
    organizationName: null, npiType: 'INDIVIDUAL', addressLine1: '3420 S Val Vista Dr', city: 'Gilbert',
    state: 'AZ', postalCode: '85297', licenseNumber: 'E91336', licenseState: 'AZ',
    taxonomy: 'Orthopaedic Surgery', status: 'ACTIVE', createdAt: '2026-09-12T15:21:33Z',
    verification: {
      id: 9, npiStatus: 'VALID', registryName: 'NAKAMURA, KENJI',
      registryAddress: '3420 S VAL VISTA DR, GILBERT, AZ 852976713', registryTaxonomy: 'Orthopaedic Surgery',
      enumerationDate: '2014-05-19', exclusionStatus: 'NOT_EXCLUDED', exclusionMatchedName: null,
      exclusionDate: null, exclusionType: null, exclusionMatchScore: 0, discrepancies: [], missingFieldCount: 0,
      source: 'LIVE_API', processingNote: 'No identifier or name match found on the exclusion list.',
      durationMs: 358, verifiedAt: '2026-09-12T15:21:35Z'
    },
    risk: {
      id: 9, score: 7.6, anomalyScore: 0.508, anomalous: false, confidence: 0.23, recommendation: 'APPROVE',
      flags: [],
      featureSnapshot: '{"NPI verification risk": 0.000, "OIG exclusion risk": 0.000, "Discrepancy volume": 0.000, "Discrepancy severity": 0.000, "Missing field ratio": 0.000, "Extraction uncertainty": 0.070, "Name similarity gap": 0.000}',
      modelVersion: 'isolation-forest-120t-128s-seed42', reviewerDecision: null, reviewedBy: null,
      assessedAt: '2026-09-12T15:21:35Z'
    },
    documents: [{ id: 9, filename: '12-scanned-individual.png', documentType: 'Primary source verification letter',
      status: 'PARSED', engine: 'TESSERACT_OCR', confidence: 0.93, sizeBytes: 32431, uploadedBy: 'reviewer',
      uploadedAt: '2026-09-12T15:21:33Z', textPreview: 'ARIZONA MEDICAL BOARD PRIMARY SOURCE VERIFICATION Provider Name: Nakamura, Kenji, M.D. ...', purged: false }]
  }
];

const summary = p => ({
  id: p.id, npi: p.npi, displayName: p.displayName, npiType: p.npiType, status: p.status,
  npiStatus: p.verification ? p.verification.npiStatus : null,
  exclusionStatus: p.verification ? p.verification.exclusionStatus : null,
  riskScore: p.risk ? p.risk.score : null,
  recommendation: p.risk ? p.risk.recommendation : null,
  state: p.state, lastVerifiedAt: p.verification ? p.verification.verifiedAt : null
});

const auditEntries = [
  ['LOGIN_SUCCESS', 'SECURITY', 'Session opened', 'SUCCESS', 'reviewer', 'REVIEWER'],
  ['DOCUMENT_UPLOAD', 'INTAKE', 'Received 12-scanned-individual.png', 'SUCCESS', 'reviewer', 'REVIEWER'],
  ['VERIFICATION_RUN', 'VERIFICATION', 'NPI status VALID, exclusion status NOT_EXCLUDED, source LIVE_API', 'SUCCESS', 'reviewer', 'REVIEWER'],
  ['RISK_ASSESSED', 'INTELLIGENCE', 'Risk score 7.6, recommendation APPROVE, completed in 1934 ms', 'SUCCESS', 'reviewer', 'REVIEWER'],
  ['DOCUMENT_UPLOAD', 'INTAKE', 'Received 08-possible-exclusion-name-match.txt', 'SUCCESS', 'reviewer', 'REVIEWER'],
  ['VERIFICATION_RUN', 'VERIFICATION', 'NPI status NOT_FOUND, exclusion status POSSIBLE_MATCH, source LIVE_API', 'SUCCESS', 'reviewer', 'REVIEWER'],
  ['RISK_ASSESSED', 'INTELLIGENCE', 'Risk score 60.2, recommendation REVIEW, completed in 412 ms', 'SUCCESS', 'reviewer', 'REVIEWER'],
  ['DECISION_RECORDED', 'REVIEW', 'Reviewer recorded APPROVED against a platform recommendation of APPROVE. Note supplied.', 'SUCCESS', 'reviewer', 'REVIEWER'],
  ['REPORT_EXPORT', 'REPORTING', 'Credential Verification Report generated for provider 7', 'SUCCESS', 'reviewer', 'REVIEWER'],
  ['DOCUMENT_UPLOAD', 'INTAKE', 'Received 02-excluded-provider.txt', 'SUCCESS', 'reviewer', 'REVIEWER'],
  ['VERIFICATION_RUN', 'VERIFICATION', 'NPI status VALID, exclusion status EXCLUDED, source LIVE_API', 'SUCCESS', 'reviewer', 'REVIEWER'],
  ['LOGIN_FAILURE', 'SECURITY', 'Incorrect password supplied', 'FAILURE', 'viewer', 'VIEWER'],
  ['LOGIN_SUCCESS', 'SECURITY', 'Session opened', 'SUCCESS', 'admin', 'ADMIN'],
  ['RETENTION_PURGE_MANUAL', 'STORAGE', 'Administrator ran the retention purge manually', 'SUCCESS', 'admin', 'ADMIN']
].map((e, i) => ({
  id: 100 - i,
  timestamp: new Date(Date.parse('2026-09-12T15:24:00Z') - i * 137000).toISOString(),
  username: e[4], role: e[5], action: e[0], module: e[1],
  entityType: null, entityId: null, detail: e[2], outcome: e[3], ipAddress: '127.0.0.1'
}));

const routes = {
  'POST /api/auth/login': () => ({
    token: 'preview-session-token', username: 'reviewer',
    displayName: 'Credentialing Specialist', role: 'ADMIN', sessionTimeoutMinutes: 30
  }),
  'POST /api/auth/logout': () => ({}),
  'GET /api/auth/me': () => ({ username: 'reviewer', displayName: 'Credentialing Specialist', role: 'ADMIN' }),
  'GET /api/health': () => ({ status: 'UP' }),
  'GET /api/providers': () => providers.map(summary),
  'GET /api/dashboard/metrics': () => ({
    totalProviders: 9, verifiedProviders: 3, flaggedProviders: 4, rejectedProviders: 2,
    manualReviewProviders: 0, exclusionHits: 1, possibleExclusionHits: 1, npiNotFound: 1,
    anomaliesDetected: 5, averageRiskScore: 28.8, averageVerificationMs: 277.3,
    statusBreakdown: { PENDING: 0, ACTIVE: 3, FLAGGED: 4, REJECTED: 2, MANUAL_REVIEW: 0 },
    recommendationBreakdown: { APPROVE: 3, REVIEW: 4, REJECT: 2 },
    recentProviders: providers.map(summary)
  }),
  'GET /api/dashboard/review-queue': () =>
    providers.filter(p => p.risk && !p.risk.reviewerDecision)
             .sort((a, b) => b.risk.score - a.risk.score).map(summary),
  'GET /api/audit': () => auditEntries,
  'GET /api/admin/health': () => ({
    nppesEnabled: true, nppesReachable: true, nppesLastError: null, offlineSnapshotSize: 14,
    exclusionListLoaded: true, exclusionRecordCount: 20,
    ocrEngines: ['PLAIN_TEXT: available', 'PDF_TEXT_LAYER: available', 'TESSERACT_OCR: available'],
    modelVersion: 'isolation-forest-120t-128s-seed42', anomalyThreshold: 0.6,
    precision: 0.893, recall: 1.0, f1Score: 0.943, accuracy: 0.97, evaluationSampleCount: 400,
    uploadDirectory: 'C:\\credentialing\\uploads', retentionDays: 7
  }),
  'GET /api/admin/users': () => ([
    { id: 1, username: 'admin', displayName: 'System Administrator', email: 'admin@example.edu',
      role: 'ADMIN', active: true, lastLoginAt: '2026-09-12T15:23:00Z' },
    { id: 2, username: 'reviewer', displayName: 'Credentialing Specialist', email: 'reviewer@example.edu',
      role: 'REVIEWER', active: true, lastLoginAt: '2026-09-12T15:03:00Z' },
    { id: 3, username: 'viewer', displayName: 'Compliance Officer', email: 'viewer@example.edu',
      role: 'VIEWER', active: true, lastLoginAt: null }
  ]),
  'POST /api/admin/retention/purge': () => ({ purgedDocuments: 0, retentionDays: 7 }),
  'POST /api/documents/upload': () => ({
    providerId: 3, documentId: 3, displayName: 'Okafor, Chidi', npi: '1283947566',
    status: 'FLAGGED', npiStatus: 'VALID', exclusionStatus: 'NOT_EXCLUDED',
    riskScore: 21.0, recommendation: 'REVIEW', elapsedMs: 438, warning: null
  })
};

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css',
               '.ico': 'image/x-icon', '.json': 'application/json', '.map': 'application/json' };

http.createServer((req, res) => {
  const url = req.url.split('?')[0];
  const key = `${req.method} ${url}`;

  if (routes[key]) {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(routes[key]()));
    return;
  }

  const providerMatch = url.match(/^\/api\/providers\/(\d+)$/);
  if (req.method === 'GET' && providerMatch) {
    const found = providers.find(p => p.id === Number(providerMatch[1]));
    res.writeHead(found ? 200 : 404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(found ? { ...found, riskHistory: [] } : { message: 'Not found' }));
    return;
  }

  if (url.startsWith('/api/')) {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end('{}');
    return;
  }

  // Static assets, with an index.html fallback for Angular's client-side routes.
  let file = path.join(DIST, url === '/' ? 'index.html' : url.slice(1));
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    file = path.join(DIST, 'index.html');
  }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
  res.end(fs.readFileSync(file));
}).listen(PORT, () => console.log(`UI preview server listening on http://localhost:${PORT}`));
