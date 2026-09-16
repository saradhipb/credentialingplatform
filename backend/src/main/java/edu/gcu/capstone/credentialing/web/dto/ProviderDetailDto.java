package edu.gcu.capstone.credentialing.web.dto;

import java.util.List;

/**
 * ProviderDetailDto.java
 *
 * <p>The complete record shown on the provider detail screen: the identifying
 * fields held on file, every document submitted, the latest evidence, the latest
 * score, and the history of previous assessments.</p>
 *
 * @param id               provider identifier
 * @param npi              the ten-digit identifier
 * @param displayName      provider or organization name
 * @param firstName        given name
 * @param lastName         family name
 * @param organizationName legal business name
 * @param npiType          INDIVIDUAL, ORGANIZATION or UNKNOWN
 * @param addressLine1     street address on file
 * @param city             city on file
 * @param state            state on file
 * @param postalCode       postal code on file
 * @param licenseNumber    license number on file
 * @param licenseState     licensing state on file
 * @param taxonomy         specialty on file
 * @param status           workflow status
 * @param createdAt        ISO-8601 creation timestamp
 * @param documents        documents submitted for this provider
 * @param verification     the most recent verification evidence
 * @param risk             the most recent risk assessment
 * @param riskHistory      earlier assessments, newest first
 */
public record ProviderDetailDto(Long id, String npi, String displayName, String firstName,
                                String lastName, String organizationName, String npiType,
                                String addressLine1, String city, String state, String postalCode,
                                String licenseNumber, String licenseState, String taxonomy,
                                String status, String createdAt,
                                List<DocumentDto> documents,
                                VerificationDto verification,
                                RiskDto risk,
                                List<RiskDto> riskHistory) {
}
