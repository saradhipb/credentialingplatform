/**
 * status.ts
 *
 * Translates the API's status codes into the wording and the visual tone the
 * interface uses.
 *
 * Two things are deliberate here. The labels are written in plain language,
 * because a credentialing specialist should not have to learn the platform's
 * internal vocabulary to read a screen. And every label stands on its own
 * without its colour, so the meaning survives a monochrome display, a colour
 * vision deficiency, or a printed page.
 */

/** The four visual tones used by badges, meters and tiles. */
export type Tone = 'ok' | 'warn' | 'danger' | 'info';

/** Plain-language label for a provider workflow status. */
export function providerStatusLabel(status: string | null): string {
  switch (status) {
    case 'ACTIVE': return 'Verified';
    case 'FLAGGED': return 'Needs review';
    case 'REJECTED': return 'Not eligible';
    case 'MANUAL_REVIEW': return 'Manual review';
    case 'PENDING': return 'Pending';
    default: return status ?? 'Unknown';
  }
}

/** Visual tone for a provider workflow status. */
export function providerStatusTone(status: string | null): Tone {
  switch (status) {
    case 'ACTIVE': return 'ok';
    case 'FLAGGED': return 'warn';
    case 'REJECTED': return 'danger';
    case 'MANUAL_REVIEW': return 'warn';
    default: return 'info';
  }
}

/** Plain-language label for the CMS registry check. */
export function npiStatusLabel(status: string | null): string {
  switch (status) {
    case 'VALID': return 'Active in CMS registry';
    case 'DEACTIVATED': return 'Deactivated in CMS registry';
    case 'NOT_FOUND': return 'Not found in CMS registry';
    case 'INVALID_FORMAT': return 'Identifier failed check digit';
    case 'MISSING': return 'No identifier supplied';
    case 'UNAVAILABLE': return 'Registry unavailable';
    default: return status ?? 'Not checked';
  }
}

/** Visual tone for the CMS registry check. */
export function npiStatusTone(status: string | null): Tone {
  switch (status) {
    case 'VALID': return 'ok';
    case 'DEACTIVATED':
    case 'NOT_FOUND':
    case 'INVALID_FORMAT':
    case 'MISSING': return 'danger';
    case 'UNAVAILABLE': return 'warn';
    default: return 'info';
  }
}

/** Plain-language label for the OIG exclusion screen. */
export function exclusionStatusLabel(status: string | null): string {
  switch (status) {
    case 'NOT_EXCLUDED': return 'No exclusion found';
    case 'EXCLUDED': return 'Excluded by OIG';
    case 'POSSIBLE_MATCH': return 'Possible name match';
    case 'UNAVAILABLE': return 'Screening unavailable';
    default: return status ?? 'Not screened';
  }
}

/** Visual tone for the OIG exclusion screen. */
export function exclusionStatusTone(status: string | null): Tone {
  switch (status) {
    case 'NOT_EXCLUDED': return 'ok';
    case 'EXCLUDED': return 'danger';
    case 'POSSIBLE_MATCH': return 'warn';
    default: return 'info';
  }
}

/** Plain-language label for the platform's advisory recommendation. */
export function recommendationLabel(recommendation: string | null): string {
  switch (recommendation) {
    case 'APPROVE': return 'Approve';
    case 'REVIEW': return 'Review';
    case 'REJECT': return 'Reject';
    default: return recommendation ?? 'Not assessed';
  }
}

/** Visual tone for the advisory recommendation. */
export function recommendationTone(recommendation: string | null): Tone {
  switch (recommendation) {
    case 'APPROVE': return 'ok';
    case 'REVIEW': return 'warn';
    case 'REJECT': return 'danger';
    default: return 'info';
  }
}

/** Visual tone for a 0-100 composite risk score. */
export function riskTone(score: number | null): Tone {
  if (score === null || score === undefined) {
    return 'info';
  }
  if (score >= 70) { return 'danger'; }
  if (score >= 40) { return 'warn'; }
  return 'ok';
}

/** Plain-language description of where a verification result came from. */
export function sourceLabel(source: string | null): string {
  switch (source) {
    case 'LIVE_API': return 'Live CMS registry lookup';
    case 'OFFLINE_FALLBACK': return 'Offline snapshot (registry unreachable)';
    case 'NONE': return 'No registry lookup was made';
    default: return source ?? 'Unknown';
  }
}
