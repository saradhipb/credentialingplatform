package edu.gcu.capstone.credentialing.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.gcu.capstone.credentialing.domain.*;
import edu.gcu.capstone.credentialing.verification.Discrepancy;
import edu.gcu.capstone.credentialing.web.dto.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * DtoMapper.java
 *
 * <p>Converts persistence entities into the response objects the API returns.
 * Keeping this translation in one class means the REST contract is defined in
 * exactly one place, and an entity can be reshaped without changing what clients
 * receive.</p>
 */
@Component
public class DtoMapper {

    /** Characters of extracted text returned as a preview. */
    private static final int PREVIEW_LENGTH = 400;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Builds a queue row for one provider.
     *
     * @param provider     the provider
     * @param verification the most recent verification, may be null
     * @param risk         the most recent assessment, may be null
     * @return the summary row
     */
    public ProviderSummaryDto toSummary(Provider provider, VerificationRecord verification,
                                        RiskAssessment risk) {
        return new ProviderSummaryDto(
                provider.getId(),
                provider.getNpi(),
                provider.getDisplayName(),
                provider.getNpiType().name(),
                provider.getStatus().name(),
                verification == null ? null : verification.getNpiStatus().name(),
                verification == null ? null : verification.getExclusionStatus().name(),
                risk == null ? null : risk.getScore(),
                risk == null ? null : risk.getRecommendation().name(),
                provider.getState(),
                verification == null ? null : format(verification.getVerifiedAt()));
    }

    /**
     * Builds the full provider detail response.
     *
     * @param provider     the provider
     * @param documents    documents submitted for the provider
     * @param verification the most recent verification, may be null
     * @param risk         the most recent assessment, may be null
     * @param riskHistory  earlier assessments, newest first
     * @return the detail response
     */
    public ProviderDetailDto toDetail(Provider provider, List<CredentialDocument> documents,
                                      VerificationRecord verification, RiskAssessment risk,
                                      List<RiskAssessment> riskHistory) {
        return new ProviderDetailDto(
                provider.getId(),
                provider.getNpi(),
                provider.getDisplayName(),
                provider.getFirstName(),
                provider.getLastName(),
                provider.getOrganizationName(),
                provider.getNpiType().name(),
                provider.getAddressLine1(),
                provider.getCity(),
                provider.getState(),
                provider.getPostalCode(),
                provider.getLicenseNumber(),
                provider.getLicenseState(),
                provider.getTaxonomy(),
                provider.getStatus().name(),
                format(provider.getCreatedAt()),
                documents.stream().map(this::toDocumentDto).toList(),
                verification == null ? null : toVerificationDto(verification),
                risk == null ? null : toRiskDto(risk),
                riskHistory.stream().map(this::toRiskDto).toList());
    }

    /**
     * Builds a document metadata response, including a short text preview.
     *
     * @param document the stored document
     * @return the response object
     */
    public DocumentDto toDocumentDto(CredentialDocument document) {
        String text = document.getExtractedText();
        String preview = text == null ? null
                : (text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "...");

        return new DocumentDto(
                document.getId(),
                document.getOriginalFilename(),
                document.getDocumentType(),
                document.getStatus().name(),
                document.getExtractionEngine(),
                document.getExtractionConfidence(),
                document.getSizeBytes(),
                document.getUploadedBy(),
                format(document.getUploadedAt()),
                preview,
                document.getPurgedAt() != null);
    }

    /**
     * Builds the verification evidence response, deserialising the stored
     * discrepancy list so the client receives structured items.
     *
     * @param record the verification evidence
     * @return the response object
     */
    public VerificationDto toVerificationDto(VerificationRecord record) {
        List<Discrepancy> discrepancies = List.of();
        String json = record.getDiscrepanciesJson();
        if (json != null && !json.isBlank()) {
            try {
                discrepancies = objectMapper.readValue(json,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Discrepancy.class));
            } catch (Exception ex) {
                discrepancies = List.of();
            }
        }

        return new VerificationDto(
                record.getId(),
                record.getNpiStatus().name(),
                record.getRegistryName(),
                record.getRegistryAddress(),
                record.getRegistryTaxonomy(),
                record.getRegistryEnumerationDate(),
                record.getExclusionStatus().name(),
                record.getExclusionMatchedName(),
                record.getExclusionDate(),
                record.getExclusionType(),
                record.getExclusionMatchScore(),
                discrepancies,
                record.getMissingFieldCount(),
                record.getSource().name(),
                record.getProcessingNote(),
                record.getDurationMs(),
                format(record.getVerifiedAt()));
    }

    /**
     * Builds the risk response, splitting the stored flag string into items.
     *
     * @param assessment the risk assessment
     * @return the response object
     */
    public RiskDto toRiskDto(RiskAssessment assessment) {
        String flags = assessment.getFlags();
        List<String> flagList = flags == null || flags.isBlank()
                ? List.of()
                : Arrays.stream(flags.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();

        return new RiskDto(
                assessment.getId(),
                assessment.getScore(),
                assessment.getAnomalyScore(),
                assessment.isAnomalous(),
                assessment.getConfidence(),
                assessment.getRecommendation().name(),
                flagList,
                assessment.getFeatureSnapshot(),
                assessment.getModelVersion(),
                assessment.getReviewerDecision(),
                assessment.getReviewedBy(),
                format(assessment.getAssessedAt()));
    }

    /**
     * Builds an audit trail row.
     *
     * @param entry the stored audit entry
     * @return the response object
     */
    public AuditLogDto toAuditDto(AuditLog entry) {
        return new AuditLogDto(
                entry.getId(),
                format(entry.getTimestamp()),
                entry.getUsername(),
                entry.getRole(),
                entry.getAction(),
                entry.getModule(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getDetail(),
                entry.getOutcome(),
                entry.getIpAddress());
    }

    /**
     * Builds a user-management row.
     *
     * @param user the account
     * @return the response object
     */
    public UserDto toUserDto(AppUser user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole().name(),
                user.isActive(),
                format(user.getLastLoginAt()));
    }

    /** Formats an instant as ISO-8601, tolerating null. */
    private String format(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
