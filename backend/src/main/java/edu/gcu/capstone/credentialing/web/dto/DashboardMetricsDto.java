package edu.gcu.capstone.credentialing.web.dto;

import java.util.List;
import java.util.Map;

/**
 * DashboardMetricsDto.java
 *
 * <p>Everything the Verification Dashboard needs in a single response. One
 * aggregate call is used instead of several so the dashboard renders from one
 * consistent snapshot rather than from figures gathered moments apart.</p>
 *
 * @param totalProviders        providers on file
 * @param verifiedProviders     providers in the ACTIVE state
 * @param flaggedProviders      providers awaiting review
 * @param rejectedProviders     providers with a disqualifying finding
 * @param manualReviewProviders providers whose automated processing did not complete
 * @param exclusionHits         confirmed OIG exclusion matches
 * @param possibleExclusionHits name matches awaiting confirmation
 * @param npiNotFound           identifiers the registry did not recognise
 * @param anomaliesDetected     assessments the model flagged
 * @param averageRiskScore      mean composite risk score
 * @param averageVerificationMs mean external verification round trip
 * @param statusBreakdown       provider count by workflow status
 * @param recommendationBreakdown assessment count by recommendation
 * @param recentProviders       the most recently processed providers
 */
public record DashboardMetricsDto(long totalProviders, long verifiedProviders,
                                  long flaggedProviders, long rejectedProviders,
                                  long manualReviewProviders, long exclusionHits,
                                  long possibleExclusionHits, long npiNotFound,
                                  long anomaliesDetected, double averageRiskScore,
                                  double averageVerificationMs,
                                  Map<String, Long> statusBreakdown,
                                  Map<String, Long> recommendationBreakdown,
                                  List<ProviderSummaryDto> recentProviders) {
}
