package edu.gcu.capstone.credentialing.web.dto;

/**
 * UploadResponseDto.java
 *
 * <p>Returned immediately after a document is processed, so the upload screen can
 * show the outcome without a second request.</p>
 *
 * @param providerId   the provider the document was attached to
 * @param documentId   the stored document
 * @param displayName  the provider name that was read
 * @param npi          the identifier that was read
 * @param status       the provider's resulting workflow status
 * @param npiStatus    outcome of the registry check, null if not reached
 * @param exclusionStatus outcome of the exclusion screen, null if not reached
 * @param riskScore    composite risk score, null if not reached
 * @param recommendation advisory recommendation, null if not reached
 * @param elapsedMs    total processing time
 * @param warning      a message to show the user, or null
 */
public record UploadResponseDto(Long providerId, Long documentId, String displayName, String npi,
                                String status, String npiStatus, String exclusionStatus,
                                Double riskScore, String recommendation, long elapsedMs,
                                String warning) {
}
