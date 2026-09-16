package edu.gcu.capstone.credentialing.web.dto;

/**
 * ProviderSummaryDto.java
 *
 * <p>One row of the verification queue table. Summaries are assembled in the
 * service layer rather than serialising entities directly, which keeps lazily
 * loaded associations out of the JSON and means a change to the database schema
 * does not silently change the API.</p>
 *
 * @param id             provider identifier
 * @param npi            the ten-digit identifier, may be null
 * @param displayName    provider or organization name
 * @param npiType        INDIVIDUAL, ORGANIZATION or UNKNOWN
 * @param status         workflow status
 * @param npiStatus      outcome of the registry check
 * @param exclusionStatus outcome of the exclusion screen
 * @param riskScore      composite 0-100 risk score, null when not yet scored
 * @param recommendation advisory recommendation, null when not yet scored
 * @param state          practice state
 * @param lastVerifiedAt ISO-8601 timestamp of the most recent verification
 */
public record ProviderSummaryDto(Long id, String npi, String displayName, String npiType,
                                 String status, String npiStatus, String exclusionStatus,
                                 Double riskScore, String recommendation, String state,
                                 String lastVerifiedAt) {
}
