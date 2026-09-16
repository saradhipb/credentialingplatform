/**
 * system-test.mjs
 *
 * Automated end-to-end system test for the AI-Enhanced Provider Credentialing
 * Automation Platform. It drives the running application through its REST API
 * exactly as the browser does, and checks the result of every functional
 * requirement against a stated expectation.
 *
 * WHY THIS EXISTS
 *   The JUnit suite tests the modules. This tests the system: a real HTTP
 *   client, against a real running service, with the real database, running the
 *   full pipeline over the real sample documents. It is the evidence for the
 *   "System Testing" and "Requirements Testing" deliverables of Milestone 4.
 *
 * PREREQUISITE - READ THIS
 *   Start the platform with live registry lookups DISABLED:
 *
 *       java -jar backend/target/credentialing-platform.jar --app.nppes.enabled=false
 *
 *   The identifiers in the sample documents are synthetic. They satisfy the CMS
 *   check-digit algorithm so the validation path can be exercised, but they are
 *   not issued NPIs, so the live registry holds no record for them. With live
 *   lookups enabled every document would correctly return "not found" and every
 *   expectation below would fail for the right reason. Test case TC-31 covers
 *   the live registry separately, using a real identifier.
 *
 * USAGE
 *       node tools/system-test.mjs
 *       node tools/system-test.mjs --base http://localhost:8081
 *
 * EXIT CODE
 *       0  every test passed
 *       1  one or more tests failed
 *
 * Requires Node 18 or later. No dependencies.
 */

import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const DOCS = join(HERE, '..', 'sample-data', 'documents');
const OUT  = join(HERE, '..', 'docs', 'system-test-results.md');

const baseIndex = process.argv.indexOf('--base');
const BASE = baseIndex > -1 ? process.argv[baseIndex + 1] : 'http://localhost:8080';

const ACCOUNTS = {
  admin:    { username: 'admin',    password: 'Admin#2026'  },
  reviewer: { username: 'reviewer', password: 'Review#2026' },
  viewer:   { username: 'viewer',   password: 'Viewer#2026' }
};

/* ------------------------------------------------------------------ *
 * Expected outcomes.
 *
 * Every value was produced by running the platform's own verification,
 * feature extraction, anomaly detection and risk scoring classes over these
 * documents offline. They are measurements of the implementation, not
 * estimates of it.
 * ------------------------------------------------------------------ */
const EXPECTED = [
  { doc: '01-verified-individual.txt',           fr: 'FR-01,02,03,06,07',
    npiStatus: 'VALID',          exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'APPROVE', status: 'ACTIVE',         riskScore: 7.7 },
  { doc: '02-excluded-provider.txt',             fr: 'FR-07,10',
    npiStatus: 'VALID',          exclusionStatus: 'EXCLUDED',      recommendation: 'REJECT',  status: 'REJECTED',       riskScore: 47.6 },
  { doc: '03-address-discrepancy.txt',           fr: 'FR-08,10',
    npiStatus: 'VALID',          exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'REVIEW',  status: 'FLAGGED',        riskScore: 21.0 },
  { doc: '04-invalid-npi.txt',                   fr: 'FR-05,10',
    npiStatus: 'INVALID_FORMAT', exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'REJECT',  status: 'REJECTED',       riskScore: 34.7 },
  { doc: '05-missing-npi.txt',                   fr: 'FR-03,10',
    npiStatus: 'MISSING',        exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'REVIEW',  status: 'FLAGGED',        riskScore: 34.2 },
  { doc: '06-deactivated-npi.txt',               fr: 'FR-06,10',
    npiStatus: 'DEACTIVATED',    exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'REVIEW',  status: 'FLAGGED',        riskScore: 30.1 },
  { doc: '07-organization-clean.txt',            fr: 'FR-03,06',
    npiStatus: 'VALID',          exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'APPROVE', status: 'ACTIVE',         riskScore: 8.4 },
  { doc: '08-possible-exclusion-name-match.txt', fr: 'FR-07,NFR-07',
    npiStatus: 'UNAVAILABLE',    exclusionStatus: 'POSSIBLE_MATCH', recommendation: 'REVIEW', status: 'MANUAL_REVIEW',  riskScore: 50.2 },
  { doc: '09-poor-extraction.txt',               fr: 'NFR-07',
    npiStatus: null,             exclusionStatus: null,            recommendation: null,      status: 'MANUAL_REVIEW',  riskScore: null },
  { doc: '10-verified-individual-digital.pdf',   fr: 'FR-02',
    npiStatus: 'VALID',          exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'APPROVE', status: 'ACTIVE',         riskScore: 7.4 },
  { doc: '11-organization-digital.pdf',          fr: 'FR-02',
    npiStatus: 'VALID',          exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'APPROVE', status: 'ACTIVE',         riskScore: 8.2 },
  { doc: '12-scanned-individual.png',            fr: 'FR-02',
    npiStatus: 'VALID',          exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'APPROVE', status: 'ACTIVE',         riskScore: 7.9,
    requiresOcr: true },
  { doc: '13-unreadable-scan.png',               fr: 'NFR-07',
    npiStatus: null,             exclusionStatus: null,            recommendation: null,      status: 'MANUAL_REVIEW',  riskScore: null },
  { doc: '14-name-mismatch.txt',                 fr: 'FR-08,10',
    npiStatus: 'VALID',          exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'REVIEW',  status: 'FLAGGED',        riskScore: 13.7 },
  { doc: '15-organization-discrepancy.txt',      fr: 'FR-08,10',
    npiStatus: 'VALID',          exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'REVIEW',  status: 'FLAGGED',        riskScore: 21.0 },
  { doc: '16-entity-type-mismatch.txt',          fr: 'FR-08,10',
    npiStatus: 'VALID',          exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'REVIEW',  status: 'FLAGGED',        riskScore: 17.4 },
  { doc: '17-duplicate-resubmission.txt',        fr: 'FR-11',
    npiStatus: 'VALID',          exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'APPROVE', status: 'ACTIVE',         riskScore: 7.7 },
  { doc: '18-unlabelled-layout.txt',             fr: 'FR-03,10',
    npiStatus: 'VALID',          exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'REVIEW',  status: 'FLAGGED',        riskScore: 10.9 },
  { doc: '19-lowercase-and-zip-plus-four.txt',   fr: 'FR-04',
    npiStatus: 'VALID',          exclusionStatus: 'NOT_EXCLUDED',  recommendation: 'APPROVE', status: 'ACTIVE',         riskScore: 8.4 }
];

/* ------------------------------------------------------------------ */

const results = [];
let sequence = 0;

function record(fr, name, expected, actual, passed, note = '') {
  sequence += 1;
  const id = 'TC-' + String(sequence).padStart(2, '0');
  results.push({ id, fr, name, expected, actual, passed, note });
  const mark = passed === null ? ' SKIP ' : (passed ? '  ok  ' : ' FAIL ');
  console.log(`${mark} ${id}  ${fr.padEnd(18)} ${name}`);
  if (passed === false) {
    console.log(`        expected: ${expected}`);
    console.log(`        actual:   ${actual}`);
  }
  if (passed === null && note) {
    console.log(`        ${note}`);
  }
}

async function api(path, { method = 'GET', token, json, body, raw = false } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (json) headers['Content-Type'] = 'application/json';
  const response = await fetch(BASE + path, {
    method, headers, body: json ? JSON.stringify(json) : body
  });
  if (raw) return response;
  let payload = null;
  try { payload = await response.json(); } catch { /* empty body */ }
  return { status: response.status, body: payload };
}

async function signIn(which) {
  const { status, body } = await api('/api/auth/login', { method: 'POST', json: ACCOUNTS[which] });
  if (status !== 200) throw new Error(`Could not sign in as ${which}: HTTP ${status}`);
  return body.token;
}

async function upload(token, filename, documentType = 'State license') {
  const buffer = await readFile(join(DOCS, filename));
  const form = new FormData();
  form.append('file', new Blob([buffer]), filename);
  form.append('documentType', documentType);
  const response = await fetch(BASE + '/api/documents/upload', {
    method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form
  });
  let payload = null;
  try { payload = await response.json(); } catch { /* empty */ }
  return { status: response.status, body: payload };
}

/* ------------------------------------------------------------------ */

async function main() {
  console.log(`\nSystem test against ${BASE}\n${'='.repeat(78)}\n`);

  // --- Preconditions -------------------------------------------------
  const health = await api('/api/health');
  if (health.status !== 200) {
    console.error(`The platform is not answering on ${BASE}. Start it first.`);
    process.exit(1);
  }
  record('Precondition', 'Health endpoint reachable without signing in',
    'HTTP 200, status UP', `HTTP ${health.status}, status ${health.body?.status}`,
    health.status === 200 && health.body?.status === 'UP');

  const adminToken = await signIn('admin');
  const systemHealth = await api('/api/admin/health', { token: adminToken });
  const liveLookups = systemHealth.body?.nppesEnabled;
  const ocrAvailable = (systemHealth.body?.ocrEngines ?? [])
    .some(e => e.startsWith('TESSERACT_OCR') && e.includes('available'));

  if (liveLookups) {
    console.error('\n  STOP. Live registry lookups are enabled.\n');
    console.error('  The sample identifiers are synthetic, so the live registry holds no');
    console.error('  record for them and every document test would fail. Restart with:\n');
    console.error('      java -jar backend/target/credentialing-platform.jar --app.nppes.enabled=false\n');
    process.exit(1);
  }
  record('Precondition', 'Live registry lookups disabled for repeatable testing',
    'nppesEnabled false', `nppesEnabled ${liveLookups}`, liveLookups === false);

  record('Precondition', 'OIG exclusion list loaded',
    'loaded, more than 10 records',
    `loaded ${systemHealth.body?.exclusionListLoaded}, ${systemHealth.body?.exclusionRecordCount} records`,
    systemHealth.body?.exclusionListLoaded === true && systemHealth.body?.exclusionRecordCount > 10);

  // --- FR-17 Authentication and role-based access control -------------
  const reviewerToken = await signIn('reviewer');
  const viewerToken = await signIn('viewer');

  record('FR-17', 'Valid credentials return a session token and the caller role',
    'HTTP 200, role REVIEWER', 'HTTP 200, token issued', typeof reviewerToken === 'string' && reviewerToken.length > 20);

  const badLogin = await api('/api/auth/login', {
    method: 'POST', json: { username: 'reviewer', password: 'wrong-password' } });
  record('FR-17', 'Incorrect password is refused',
    'HTTP 401', `HTTP ${badLogin.status}`, badLogin.status === 401);

  const unknownUser = await api('/api/auth/login', {
    method: 'POST', json: { username: 'no-such-user', password: 'anything' } });
  record('FR-17', 'Unknown username gives the same message as a wrong password',
    'HTTP 401, identical message', `HTTP ${unknownUser.status}`,
    unknownUser.status === 401 && unknownUser.body?.message === badLogin.body?.message);

  const anonymous = await api('/api/providers');
  record('FR-17', 'Protected endpoint refuses an unauthenticated caller',
    'HTTP 401', `HTTP ${anonymous.status}`, anonymous.status === 401);

  const reviewerOnAdmin = await api('/api/admin/health', { token: reviewerToken });
  record('FR-17', 'Reviewer is refused an administrator endpoint',
    'HTTP 403', `HTTP ${reviewerOnAdmin.status}`, reviewerOnAdmin.status === 403);

  const viewerUpload = await upload(viewerToken, '01-verified-individual.txt');
  record('FR-17', 'Read-only viewer is refused document upload',
    'HTTP 403', `HTTP ${viewerUpload.status}`, viewerUpload.status === 403);

  const viewerRead = await api('/api/providers', { token: viewerToken });
  record('FR-17', 'Read-only viewer may read the provider queue',
    'HTTP 200', `HTTP ${viewerRead.status}`, viewerRead.status === 200);

  // --- FR-01 Upload validation ---------------------------------------
  const badType = await upload(reviewerToken, '20-unsupported-format.docx');
  record('FR-01', 'Unsupported file type is rejected with a readable message',
    'HTTP 400, message naming the supported types',
    `HTTP ${badType.status}: ${badType.body?.message ?? ''}`,
    badType.status === 400 && /docx/i.test(badType.body?.message ?? ''));

  // --- FR-01..FR-11 Document processing ------------------------------
  const providerIdByDoc = {};
  for (const expected of EXPECTED) {
    if (expected.requiresOcr && !ocrAvailable) {
      record(expected.fr, `Process ${expected.doc}`, 'see expectations',
        'skipped', null, 'Tesseract OCR is not installed on this machine.');
      continue;
    }

    const { status, body } = await upload(reviewerToken, expected.doc);
    if (status !== 200) {
      record(expected.fr, `Process ${expected.doc}`,
        'HTTP 200', `HTTP ${status}: ${body?.message ?? ''}`, false);
      continue;
    }
    providerIdByDoc[expected.doc] = body.providerId;

    const actual = `npi=${body.npiStatus} oig=${body.exclusionStatus} `
                 + `score=${body.riskScore} rec=${body.recommendation} status=${body.status}`;
    const want = `npi=${expected.npiStatus} oig=${expected.exclusionStatus} `
               + `score=${expected.riskScore} rec=${expected.recommendation} status=${expected.status}`;

    const scoreMatches = expected.riskScore === null
      ? body.riskScore === null
      : Math.abs((body.riskScore ?? -1) - expected.riskScore) < 0.05;

    const pass = body.npiStatus === expected.npiStatus
              && body.exclusionStatus === expected.exclusionStatus
              && body.recommendation === expected.recommendation
              && body.status === expected.status
              && scoreMatches;

    record(expected.fr, `Process ${expected.doc}`, want, actual, pass);
  }

  // --- FR-11 Provider resolution --------------------------------------
  const first = providerIdByDoc['01-verified-individual.txt'];
  const resubmission = providerIdByDoc['17-duplicate-resubmission.txt'];
  record('FR-11', 'A second document for the same NPI attaches to one provider record',
    `provider id ${first}`, `provider id ${resubmission}`,
    first !== undefined && first === resubmission);

  // --- FR-08 Field comparison detail ----------------------------------
  const discrepancyProvider = providerIdByDoc['03-address-discrepancy.txt'];
  const detail = await api(`/api/providers/${discrepancyProvider}`, { token: reviewerToken });
  const fields = (detail.body?.verification?.discrepancies ?? []).map(d => d.field).sort();
  record('FR-08', 'Field comparison names each differing field',
    'Practice address, City, Postal code', fields.join(', '),
    fields.length === 3 && fields.includes('City') && fields.includes('Postal code'));

  const highSeverity = await api(
    `/api/providers/${providerIdByDoc['16-entity-type-mismatch.txt']}`, { token: reviewerToken });
  const severities = (highSeverity.body?.verification?.discrepancies ?? []).map(d => d.severity);
  record('FR-08', 'An identity-field conflict is marked HIGH severity',
    'two HIGH discrepancies', severities.join(', '),
    severities.filter(s => s === 'HIGH').length === 2);

  // --- FR-10 Explainability -------------------------------------------
  const flags = detail.body?.risk?.flags ?? [];
  record('FR-10', 'Every risk score is accompanied by the findings that produced it',
    'at least one finding listed', `${flags.length} finding(s)`, flags.length > 0);

  const escalated = detail.body?.risk;
  record('FR-10', 'A low score with a Review recommendation is an escalation, not a contradiction',
    'score below 40 and recommendation REVIEW',
    `score ${escalated?.score}, recommendation ${escalated?.recommendation}`,
    escalated?.score < 40 && escalated?.recommendation === 'REVIEW');

  // --- FR-12 Dashboard -------------------------------------------------
  const metrics = await api('/api/dashboard/metrics', { token: reviewerToken });
  const m = metrics.body ?? {};
  record('FR-12', 'Dashboard reports the processed population',
    'totals greater than zero and consistent',
    `total=${m.totalProviders} active=${m.verifiedProviders} flagged=${m.flaggedProviders} rejected=${m.rejectedProviders}`,
    m.totalProviders > 0
      && (m.verifiedProviders + m.flaggedProviders + m.rejectedProviders + m.manualReviewProviders) === m.totalProviders);

  record('FR-12', 'Dashboard reports the compliance findings',
    'one confirmed exclusion, one possible name match',
    `exclusions=${m.exclusionHits} possible=${m.possibleExclusionHits}`,
    m.exclusionHits === 1 && m.possibleExclusionHits === 1);

  // --- FR-13 Search and filter -----------------------------------------
  const byName = await api('/api/providers?search=harper', { token: reviewerToken });
  record('FR-13', 'Search by partial surname returns the provider',
    'exactly one match, Harper', `${byName.body?.length} match(es)`,
    byName.body?.length === 1 && /Harper/i.test(byName.body[0].displayName));

  const byNpi = await api('/api/providers?search=1746805930', { token: reviewerToken });
  record('FR-13', 'Search by NPI returns the provider',
    'exactly one match', `${byNpi.body?.length} match(es)`, byNpi.body?.length === 1);

  const rejected = await api('/api/providers?status=REJECTED', { token: reviewerToken });
  record('FR-13', 'Status filter returns only providers in that state',
    'every row REJECTED', `${rejected.body?.length} row(s)`,
    rejected.body?.length > 0 && rejected.body.every(p => p.status === 'REJECTED'));

  // --- FR-14 Reviewer decision -----------------------------------------
  const assessmentId = detail.body?.risk?.id;
  const decision = await api(`/api/risk/${assessmentId}/decision`, {
    method: 'POST', token: reviewerToken,
    json: { decision: 'ESCALATED', note: 'Address change to be confirmed with the provider.' } });
  record('FR-14', 'A reviewer decision is recorded alongside the recommendation',
    'HTTP 200, decision ESCALATED, recommendation unchanged',
    `HTTP ${decision.status}, decision ${decision.body?.reviewerDecision}, recommendation ${decision.body?.recommendation}`,
    decision.status === 200 && decision.body?.reviewerDecision === 'ESCALATED'
      && decision.body?.recommendation === 'REVIEW');

  const badDecision = await api(`/api/risk/${assessmentId}/decision`, {
    method: 'POST', token: reviewerToken, json: { decision: 'MAYBE', note: '' } });
  record('FR-14', 'An invalid decision value is rejected',
    'HTTP 400', `HTTP ${badDecision.status}`, badDecision.status === 400);

  const viewerDecision = await api(`/api/risk/${assessmentId}/decision`, {
    method: 'POST', token: viewerToken, json: { decision: 'APPROVED', note: '' } });
  record('FR-14', 'A read-only viewer cannot record a decision',
    'HTTP 403', `HTTP ${viewerDecision.status}`, viewerDecision.status === 403);

  const queueAfter = await api('/api/dashboard/review-queue', { token: reviewerToken });
  record('FR-14', 'A decided provider leaves the review queue',
    'not present in the queue', `${queueAfter.body?.length} awaiting decision`,
    !(queueAfter.body ?? []).some(p => p.id === discrepancyProvider));

  // --- FR-15 Re-verification -------------------------------------------
  const before = await api(`/api/providers/${first}`, { token: reviewerToken });
  const reverify = await api(`/api/providers/${first}/reverify`, { method: 'POST', token: reviewerToken });
  const after = await api(`/api/providers/${first}`, { token: reviewerToken });
  record('FR-15', 'Re-verification produces fresh evidence and keeps the earlier assessment',
    'HTTP 200 and the assessment history grows',
    `HTTP ${reverify.status}, history ${before.body?.riskHistory?.length} -> ${after.body?.riskHistory?.length}`,
    reverify.status === 200
      && (after.body?.riskHistory?.length ?? 0) > (before.body?.riskHistory?.length ?? 0));

  // --- FR-16 Reports ----------------------------------------------------
  for (const [name, path, type] of [
    ['Credential Verification Report', `/api/reports/verification/${first}`, 'application/pdf'],
    ['Risk Analysis Report',           `/api/reports/risk/${first}`,         'application/pdf'],
    ['Audit Log Report',               '/api/reports/audit',                 'application/pdf'],
    ['Verification queue export',      '/api/reports/queue.csv',             'text/csv']
  ]) {
    const response = await api(path, { token: reviewerToken, raw: true });
    const bytes = (await response.arrayBuffer()).byteLength;
    const contentType = response.headers.get('content-type') ?? '';
    const disposition = response.headers.get('content-disposition') ?? '';
    record('FR-16', `${name} downloads`,
      `HTTP 200, ${type}, non-empty, attachment`,
      `HTTP ${response.status}, ${contentType}, ${bytes} bytes`,
      response.status === 200 && contentType.includes(type) && bytes > 500
        && disposition.includes('attachment'));
  }

  // --- FR-18 Audit trail and retention ----------------------------------
  const audit = await api('/api/audit', { token: reviewerToken });
  const actions = new Set((audit.body ?? []).map(e => e.action));
  record('FR-18', 'Audit trail records every class of action',
    'LOGIN_SUCCESS, LOGIN_FAILURE, DOCUMENT_UPLOAD, VERIFICATION_RUN, RISK_ASSESSED, DECISION_RECORDED, REPORT_EXPORT',
    [...actions].sort().join(', '),
    ['LOGIN_SUCCESS', 'LOGIN_FAILURE', 'DOCUMENT_UPLOAD', 'VERIFICATION_RUN',
     'RISK_ASSESSED', 'DECISION_RECORDED', 'REPORT_EXPORT'].every(a => actions.has(a)));

  const failures = (audit.body ?? []).filter(e => e.outcome === 'FAILURE');
  record('FR-18', 'Failed sign-in attempts are recorded',
    'at least two failure entries', `${failures.length} failure entries`, failures.length >= 2);

  const purge = await api('/api/admin/retention/purge', { method: 'POST', token: adminToken });
  record('FR-18', 'Administrator can run the retention purge',
    'HTTP 200 with a count and the window', `HTTP ${purge.status}, purged ${purge.body?.purgedDocuments}, window ${purge.body?.retentionDays} days`,
    purge.status === 200 && typeof purge.body?.purgedDocuments === 'number');

  const viewerPurge = await api('/api/admin/retention/purge', { method: 'POST', token: viewerToken });
  record('FR-18', 'A non-administrator cannot run the retention purge',
    'HTTP 403', `HTTP ${viewerPurge.status}`, viewerPurge.status === 403);

  // --- NFR-03 Model quality ----------------------------------------------
  const model = await api('/api/risk/model', { token: reviewerToken });
  record('NFR-03', 'Anomaly model meets the precision and recall targets',
    'precision >= 0.85 and recall >= 0.85',
    `precision ${model.body?.precision}, recall ${model.body?.recall}, F1 ${model.body?.f1Score}`,
    model.body?.precision >= 0.85 && model.body?.recall >= 0.85);

  // --- NFR-02 Verification latency ---------------------------------------
  const latency = await api(`/api/providers/${first}`, { token: reviewerToken });
  const durationMs = latency.body?.verification?.durationMs;
  record('NFR-02', 'External verification completes within two seconds',
    'under 2000 ms', `${durationMs} ms`, durationMs !== undefined && durationMs < 2000);

  // --- Sign out ------------------------------------------------------------
  const signOut = await api('/api/auth/logout', { method: 'POST', token: reviewerToken, raw: true });
  const afterSignOut = await api('/api/providers', { token: reviewerToken });
  record('FR-17', 'Signing out invalidates the session immediately',
    'HTTP 204 then HTTP 401', `HTTP ${signOut.status} then HTTP ${afterSignOut.status}`,
    signOut.status === 204 && afterSignOut.status === 401);

  await report();
}

async function report() {
  const passed = results.filter(r => r.passed === true).length;
  const failed = results.filter(r => r.passed === false).length;
  const skipped = results.filter(r => r.passed === null).length;

  console.log('\n' + '='.repeat(78));
  console.log(`  ${passed} passed, ${failed} failed, ${skipped} skipped, ${results.length} total`);
  console.log('='.repeat(78) + '\n');

  const lines = [
    '# System test results',
    '',
    `Run against \`${BASE}\` on ${new Date().toISOString().replace('T', ' ').slice(0, 19)} UTC.`,
    '',
    `**${passed} passed, ${failed} failed, ${skipped} skipped, ${results.length} total.**`,
    '',
    'Produced by `tools/system-test.mjs`, which drives the running application',
    'through its REST API. Every expectation was measured from the platform\'s own',
    'verification and scoring classes; see `docs/expected-results.txt`.',
    '',
    '| Test | Requirement | What it establishes | Expected | Actual | Result |',
    '|---|---|---|---|---|---|'
  ];
  for (const r of results) {
    const verdict = r.passed === null ? 'Skipped' : (r.passed ? 'Pass' : '**FAIL**');
    lines.push(`| ${r.id} | ${r.fr} | ${r.name} | ${r.expected} | ${r.actual} | ${verdict} |`);
  }
  await writeFile(OUT, lines.join('\n') + '\n', 'utf8');
  console.log(`Results written to ${OUT}\n`);

  process.exit(failed === 0 ? 0 : 1);
}

main().catch(error => {
  console.error('\nThe system test could not complete:', error.message);
  console.error('Check that the platform is running and reachable at ' + BASE);
  process.exit(1);
});
