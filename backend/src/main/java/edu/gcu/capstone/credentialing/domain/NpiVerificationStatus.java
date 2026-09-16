package edu.gcu.capstone.credentialing.domain;

/**
 * NpiVerificationStatus.java
 *
 * <p>Outcome of checking an NPI against the CMS NPPES registry. The distinction
 * between {@link #NOT_FOUND} and {@link #UNAVAILABLE} matters: the first is a
 * finding about the provider, the second is a finding about the registry, and
 * the two must never be treated as the same evidence.</p>
 */
public enum NpiVerificationStatus {
    /** The NPI exists in NPPES and the registry reports it as active. */
    VALID,
    /** The NPI exists but the registry reports it as deactivated. */
    DEACTIVATED,
    /** The NPI is well formed but no NPPES record exists for it. */
    NOT_FOUND,
    /** The ten-digit checksum test failed, so the number cannot be a real NPI. */
    INVALID_FORMAT,
    /** No NPI was present in the uploaded document. */
    MISSING,
    /** The registry could not be reached and no cached record was available. */
    UNAVAILABLE
}
