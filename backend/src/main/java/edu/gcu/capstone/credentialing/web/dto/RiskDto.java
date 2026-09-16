package edu.gcu.capstone.credentialing.web.dto;

import java.util.List;

/**
 * RiskDto.java
 *
 * <p>The risk panel of the detail screen. The flags are returned as a list so
 * the interface can present each reason as its own item rather than as one long
 * sentence, which is what makes the score explainable to a reviewer.</p>
 *
 * @param id              assessment identifier
 * @param score           composite 0-100 risk score
 * @param anomalyScore    unsupervised anomaly score, 0.0-1.0
 * @param anomalous       true when the anomaly score reached the threshold
 * @param confidence      how far the anomaly score sits from the threshold
 * @param recommendation  APPROVE, REVIEW or REJECT
 * @param flags           the reasons that make up the score
 * @param featureSnapshot the feature vector that was scored
 * @param modelVersion    identifier of the model that produced the score
 * @param reviewerDecision decision recorded by a human, or null
 * @param reviewedBy      who recorded the decision, or null
 * @param assessedAt      ISO-8601 timestamp
 */
public record RiskDto(Long id, double score, double anomalyScore, boolean anomalous,
                      double confidence, String recommendation, List<String> flags,
                      String featureSnapshot, String modelVersion,
                      String reviewerDecision, String reviewedBy, String assessedAt) {
}
