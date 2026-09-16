package edu.gcu.capstone.credentialing.workflow;

import edu.gcu.capstone.credentialing.domain.*;
import edu.gcu.capstone.credentialing.exception.ResourceNotFoundException;
import edu.gcu.capstone.credentialing.repository.*;
import edu.gcu.capstone.credentialing.web.DtoMapper;
import edu.gcu.capstone.credentialing.web.dto.DashboardMetricsDto;
import edu.gcu.capstone.credentialing.web.dto.ProviderDetailDto;
import edu.gcu.capstone.credentialing.web.dto.ProviderSummaryDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ProviderQueryService.java
 *
 * <p>Read-side service for the dashboard, the verification queue and the
 * provider detail screen. Separating reads from the write pipeline keeps the
 * orchestration in {@code CredentialingWorkflowService} focused on processing and
 * lets every query here run in a read-only transaction.</p>
 *
 * <p>This service supports functional requirement FR-06 (display verification and
 * risk results through a dashboard).</p>
 */
@Service
public class ProviderQueryService {

    /** Number of providers shown in the dashboard's recent-activity table. */
    private static final int RECENT_LIMIT = 12;

    private final ProviderRepository providerRepository;
    private final CredentialDocumentRepository documentRepository;
    private final VerificationRecordRepository verificationRepository;
    private final RiskAssessmentRepository riskRepository;
    private final DtoMapper mapper;

    public ProviderQueryService(ProviderRepository providerRepository,
                                CredentialDocumentRepository documentRepository,
                                VerificationRecordRepository verificationRepository,
                                RiskAssessmentRepository riskRepository,
                                DtoMapper mapper) {
        this.providerRepository = providerRepository;
        this.documentRepository = documentRepository;
        this.verificationRepository = verificationRepository;
        this.riskRepository = riskRepository;
        this.mapper = mapper;
    }

    /**
     * Returns the verification queue, optionally filtered.
     *
     * @param status     workflow status to filter by, or null for all
     * @param searchTerm free-text search across NPI and names, or null
     * @return queue rows, newest first
     */
    @Transactional(readOnly = true)
    public List<ProviderSummaryDto> listProviders(String status, String searchTerm) {
        List<Provider> providers;

        if (searchTerm != null && !searchTerm.isBlank()) {
            providers = providerRepository.search(searchTerm.trim().toLowerCase());
        } else if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            providers = providerRepository.findByStatusOrderByCreatedAtDesc(
                    ProviderStatus.valueOf(status.toUpperCase()));
        } else {
            providers = providerRepository.findAllByOrderByCreatedAtDesc();
        }

        return providers.stream().map(this::summarise).toList();
    }

    /**
     * Returns the full detail for one provider.
     *
     * @param providerId the provider to load
     * @return the detail response
     * @throws ResourceNotFoundException when no such provider exists
     */
    @Transactional(readOnly = true)
    public ProviderDetailDto getProviderDetail(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider", providerId));

        List<CredentialDocument> documents =
                documentRepository.findByProviderIdOrderByUploadedAtDesc(providerId);
        VerificationRecord verification =
                verificationRepository.findFirstByProviderIdOrderByVerifiedAtDesc(providerId).orElse(null);

        List<RiskAssessment> assessments = riskRepository.findByProviderIdOrderByAssessedAtDesc(providerId);
        RiskAssessment latest = assessments.isEmpty() ? null : assessments.get(0);
        List<RiskAssessment> history = assessments.size() > 1
                ? assessments.subList(1, assessments.size())
                : List.of();

        return mapper.toDetail(provider, documents, verification, latest, history);
    }

    /**
     * Assembles the dashboard snapshot.
     *
     * @return every figure the Verification Dashboard displays
     */
    @Transactional(readOnly = true)
    public DashboardMetricsDto getDashboardMetrics() {
        Map<String, Long> statusBreakdown = new LinkedHashMap<>();
        for (ProviderStatus status : ProviderStatus.values()) {
            statusBreakdown.put(status.name(), providerRepository.countByStatus(status));
        }

        Map<String, Long> recommendationBreakdown = new LinkedHashMap<>();
        for (RiskRecommendation recommendation : RiskRecommendation.values()) {
            recommendationBreakdown.put(recommendation.name(),
                    riskRepository.countByRecommendation(recommendation));
        }

        Double averageRisk = riskRepository.averageScore();
        Double averageDuration = verificationRepository.averageDurationMs();

        List<ProviderSummaryDto> recent = providerRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .limit(RECENT_LIMIT)
                .map(this::summarise)
                .toList();

        return new DashboardMetricsDto(
                providerRepository.count(),
                statusBreakdown.getOrDefault(ProviderStatus.ACTIVE.name(), 0L),
                statusBreakdown.getOrDefault(ProviderStatus.FLAGGED.name(), 0L),
                statusBreakdown.getOrDefault(ProviderStatus.REJECTED.name(), 0L),
                statusBreakdown.getOrDefault(ProviderStatus.MANUAL_REVIEW.name(), 0L),
                verificationRepository.countByExclusionStatus(ExclusionStatus.EXCLUDED),
                verificationRepository.countByExclusionStatus(ExclusionStatus.POSSIBLE_MATCH),
                verificationRepository.countByNpiStatus(NpiVerificationStatus.NOT_FOUND),
                riskRepository.countByAnomalousTrue(),
                averageRisk == null ? 0.0 : Math.round(averageRisk * 10.0) / 10.0,
                averageDuration == null ? 0.0 : Math.round(averageDuration * 10.0) / 10.0,
                statusBreakdown,
                recommendationBreakdown,
                recent);
    }

    /**
     * Returns the review queue: assessments no human has decided on yet, highest
     * risk first.
     *
     * @return queue rows ordered by descending risk
     */
    @Transactional(readOnly = true)
    public List<ProviderSummaryDto> listReviewQueue() {
        return riskRepository.findByReviewerDecisionIsNullOrderByScoreDesc().stream()
                .map(assessment -> {
                    Provider provider = assessment.getProvider();
                    VerificationRecord verification = verificationRepository
                            .findFirstByProviderIdOrderByVerifiedAtDesc(provider.getId()).orElse(null);
                    return mapper.toSummary(provider, verification, assessment);
                })
                .toList();
    }

    /** Loads the latest evidence and score for a provider and builds a queue row. */
    private ProviderSummaryDto summarise(Provider provider) {
        VerificationRecord verification = verificationRepository
                .findFirstByProviderIdOrderByVerifiedAtDesc(provider.getId()).orElse(null);
        RiskAssessment risk = riskRepository
                .findFirstByProviderIdOrderByAssessedAtDesc(provider.getId()).orElse(null);
        return mapper.toSummary(provider, verification, risk);
    }
}
