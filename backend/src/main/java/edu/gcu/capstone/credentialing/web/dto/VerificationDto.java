package edu.gcu.capstone.credentialing.web.dto;

import edu.gcu.capstone.credentialing.verification.Discrepancy;

import java.util.List;

/**
 * VerificationDto.java
 *
 * <p>The evidence panel of the verification detail screen: what each
 * authoritative source reported, what differed from the document, and where the
 * result came from.</p>
 *
 * @param id                   verification identifier
 * @param npiStatus            outcome of the registry check
 * @param registryName         provider name as the registry holds it
 * @param registryAddress      practice address as the registry holds it
 * @param registryTaxonomy     primary taxonomy as the registry holds it
 * @param enumerationDate      date the identifier was issued
 * @param exclusionStatus      outcome of the exclusion screen
 * @param exclusionMatchedName name of the matched excluded party, if any
 * @param exclusionDate        date the exclusion took effect
 * @param exclusionType        statutory basis of the exclusion
 * @param exclusionMatchScore  similarity of the best name match
 * @param discrepancies        field-level differences found
 * @param missingFieldCount    number of expected fields absent from the document
 * @param source               LIVE_API, OFFLINE_FALLBACK or NONE
 * @param processingNote       plain-language explanation of the result
 * @param durationMs           time the external checks took
 * @param verifiedAt           ISO-8601 timestamp
 */
public record VerificationDto(Long id, String npiStatus, String registryName, String registryAddress,
                              String registryTaxonomy, String enumerationDate,
                              String exclusionStatus, String exclusionMatchedName,
                              String exclusionDate, String exclusionType, double exclusionMatchScore,
                              List<Discrepancy> discrepancies, int missingFieldCount,
                              String source, String processingNote, long durationMs,
                              String verifiedAt) {
}
