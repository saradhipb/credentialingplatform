package edu.gcu.capstone.credentialing.ml;

import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import edu.gcu.capstone.credentialing.domain.ExclusionStatus;
import edu.gcu.capstone.credentialing.domain.NpiVerificationStatus;
import edu.gcu.capstone.credentialing.domain.RiskAssessment;
import edu.gcu.capstone.credentialing.domain.RiskRecommendation;
import edu.gcu.capstone.credentialing.domain.VerificationRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RiskScoringService.java
 *
 * <p>Combines the authoritative verification findings with the anomaly score
 * into a single 0-100 figure and an advisory recommendation.</p>
 *
 * <p>The weighting is deliberate and is the point at which the project's stated
 * position on machine learning is enforced in code. Exclusion and identifier
 * findings together carry 65% of the score; field discrepancies carry 20%; the
 * unsupervised anomaly score carries 15%. A model that disagrees with an
 * authoritative source therefore cannot overturn it. Two override rules make
 * this explicit: a confirmed exclusion or a malformed identifier drives the
 * recommendation to REJECT regardless of what the remaining components say.</p>
 *
 * <p>This service satisfies functional requirement FR-05 (generate risk scores).</p>
 */
@Service
public class RiskScoringService {

    private final ApplicationProperties properties;

    public RiskScoringService(ApplicationProperties properties) {
        this.properties = properties;
    }

    /**
     * Scores one verification event.
     *
     * @param verification the authoritative verification evidence
     * @param anomaly      the anomaly detector's output for the same event
     * @param features     the vector that was scored, retained for explainability
     * @return an unsaved risk assessment, linked to the verification by the caller
     */
    public RiskAssessment score(VerificationRecord verification,
                                AnomalyDetectionService.AnomalyResult anomaly,
                                FeatureVector features) {

        ApplicationProperties.Risk weights = properties.getRisk();
        List<String> flags = new ArrayList<>();

        double exclusionComponent = exclusionComponent(verification, flags);
        double npiComponent = npiComponent(verification, flags);
        double discrepancyComponent = discrepancyComponent(verification, flags);
        double anomalyComponent = anomalyComponent(anomaly, flags);

        double weighted =
                exclusionComponent * weights.getWeightExclusion()
                        + npiComponent * weights.getWeightNpi()
                        + discrepancyComponent * weights.getWeightDiscrepancy()
                        + anomalyComponent * weights.getWeightAnomaly();

        double score = Math.round(Math.max(0.0, Math.min(1.0, weighted)) * 1000.0) / 10.0;

        RiskAssessment assessment = new RiskAssessment();
        assessment.setVerificationRecord(verification);
        assessment.setProvider(verification.getProvider());
        assessment.setAnomalyScore(anomaly.score());
        assessment.setAnomalous(anomaly.anomalous());
        assessment.setConfidence(anomaly.confidence());
        assessment.setScore(score);
        assessment.setModelVersion(anomaly.modelVersion());
        assessment.setFeatureSnapshot(features.toString());
        assessment.setRecommendation(determineRecommendation(score, verification));
        assessment.setFlags(String.join(" | ", flags));
        return assessment;
    }

    /** Exclusion contribution, the heaviest single component. */
    private double exclusionComponent(VerificationRecord verification, List<String> flags) {
        return switch (verification.getExclusionStatus()) {
            case EXCLUDED -> {
                flags.add("Confirmed OIG exclusion matched by NPI");
                yield 1.0;
            }
            case POSSIBLE_MATCH -> {
                flags.add(String.format("Possible OIG name match (%.0f%% similarity) requires confirmation",
                        verification.getExclusionMatchScore() * 100));
                yield 0.70;
            }
            case UNAVAILABLE -> {
                flags.add("Exclusion list unavailable; screening incomplete");
                yield 0.40;
            }
            case NOT_EXCLUDED -> 0.0;
        };
    }

    /** Identifier contribution, based on what the registry reported. */
    private double npiComponent(VerificationRecord verification, List<String> flags) {
        NpiVerificationStatus status = verification.getNpiStatus();
        double component = switch (status) {
            case VALID -> 0.0;
            case DEACTIVATED -> {
                flags.add("NPI is present in the registry but deactivated");
                yield 0.80;
            }
            case NOT_FOUND -> {
                flags.add("NPI not found in the CMS registry");
                yield 0.90;
            }
            case INVALID_FORMAT -> {
                flags.add("Identifier failed the CMS check-digit test");
                yield 1.0;
            }
            case MISSING -> {
                flags.add("No NPI could be read from the submitted document");
                yield 0.95;
            }
            case UNAVAILABLE -> {
                flags.add("CMS registry unreachable; identifier not confirmed");
                yield 0.50;
            }
        };
        if (verification.getSource() == edu.gcu.capstone.credentialing.domain.VerificationSource.OFFLINE_FALLBACK) {
            flags.add("Result came from the offline registry snapshot, not a live lookup");
        }
        return component;
    }

    /** Discrepancy and missing-field contribution. */
    private double discrepancyComponent(VerificationRecord verification, List<String> flags) {
        int discrepancies = verification.getDiscrepancyCount();
        int missing = verification.getMissingFieldCount();

        if (discrepancies > 0) {
            flags.add(discrepancies + (discrepancies == 1
                    ? " field differs from the registry record"
                    : " fields differ from the registry record"));
        }
        if (missing > 2) {
            flags.add(missing + " expected fields were absent from the document");
        }

        double discrepancyLoad = Math.min(1.0, discrepancies / 4.0);
        double missingLoad = Math.min(1.0, missing / 7.0);
        return Math.min(1.0, 0.7 * discrepancyLoad + 0.3 * missingLoad);
    }

    /** Anomaly contribution, the lightest component by design. */
    private double anomalyComponent(AnomalyDetectionService.AnomalyResult anomaly, List<String> flags) {
        if (anomaly.anomalous()) {
            flags.add(String.format("Anomaly detector score %.2f. %s",
                    anomaly.score(), anomaly.explanation()));
        }
        return Math.max(0.0, Math.min(1.0, anomaly.score()));
    }

    /**
     * Applies the decision thresholds and the two override rules.
     *
     * @param score        the composite 0-100 score
     * @param verification the verification evidence, consulted for overrides
     * @return the advisory recommendation
     */
    private RiskRecommendation determineRecommendation(double score, VerificationRecord verification) {
        // Override 1: a confirmed exclusion is disqualifying on its own.
        if (verification.getExclusionStatus() == ExclusionStatus.EXCLUDED) {
            return RiskRecommendation.REJECT;
        }
        // Override 2: an identifier that cannot be an NPI cannot be credentialed.
        if (verification.getNpiStatus() == NpiVerificationStatus.INVALID_FORMAT) {
            return RiskRecommendation.REJECT;
        }
        if (score >= properties.getRisk().getRejectThreshold()) {
            return RiskRecommendation.REJECT;
        }
        if (score >= properties.getRisk().getReviewThreshold()) {
            return RiskRecommendation.REVIEW;
        }

        // Override 3: an unresolved finding always reaches a human, even when
        // the weighted score alone stays below the review threshold.
        //
        // This rule was added after the peer code review (Milestone 3,
        // Section 5). Field discrepancies carry twenty per cent of the score,
        // so a provider whose document disagreed with the registry on three
        // fields still scored around twenty and was recommended for approval.
        // A deactivated identifier behaved the same way. Re-weighting the
        // components would have distorted the relative importance the design
        // sets out, so the escalation is expressed as an explicit rule instead,
        // where it can be read and audited.
        if (requiresHumanConfirmation(verification)) {
            return RiskRecommendation.REVIEW;
        }
        return RiskRecommendation.APPROVE;
    }

    /**
     * Reports whether a finding is present that must be seen by a person
     * regardless of the composite score.
     *
     * @param verification the verification evidence
     * @return true when the record must not be auto-approved
     */
    private boolean requiresHumanConfirmation(VerificationRecord verification) {
        // The registry did not confirm the identifier as active.
        if (verification.getNpiStatus() != NpiVerificationStatus.VALID) {
            return true;
        }
        // Exclusion screening returned anything other than a clean result.
        if (verification.getExclusionStatus() != ExclusionStatus.NOT_EXCLUDED) {
            return true;
        }
        // The document and the registry disagree about who the provider is.
        if (verification.getHighSeverityDiscrepancyCount() > 0) {
            return true;
        }
        // Several fields disagree, which is rarely explained by formatting.
        if (verification.getDiscrepancyCount() >= 3) {
            return true;
        }
        // The submission is too sparse to stand on its own. Three or more of the
        // seven expected fields absent means the document did not carry enough
        // to identify the provider, even when the identifier itself verified.
        // Without this rule a document from which no provider name could be read
        // at all was recommended for approval on the strength of the identifier.
        return verification.getMissingFieldCount() >= 3;
    }
}
