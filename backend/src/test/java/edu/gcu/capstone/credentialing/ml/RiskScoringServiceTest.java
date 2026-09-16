package edu.gcu.capstone.credentialing.ml;

import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import edu.gcu.capstone.credentialing.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RiskScoringServiceTest.java
 *
 * <p>Module tests for the scoring engine. The override rules receive the most
 * attention, because they are what guarantee that a machine-learning score
 * cannot soften a finding made by an authoritative source: a confirmed exclusion
 * must produce a Reject recommendation even when the anomaly detector reports
 * the record as entirely ordinary.</p>
 */
class RiskScoringServiceTest {

    private RiskScoringService service;

    /** A neutral anomaly result, so each test isolates the component under test. */
    private static final AnomalyDetectionService.AnomalyResult CALM =
            new AnomalyDetectionService.AnomalyResult(0.20, false, 0.8, "typical", "test-model");

    private static final FeatureVector FEATURES =
            new FeatureVector(0.0, 0.0, 0.0, 0.0, 0.0, 0.1, 0.0);

    @BeforeEach
    void setUp() {
        service = new RiskScoringService(new ApplicationProperties());
    }

    @Test
    @DisplayName("A clean verification produces a low score and an Approve recommendation")
    void cleanVerificationIsApproved() {
        VerificationRecord record = verification(NpiVerificationStatus.VALID,
                ExclusionStatus.NOT_EXCLUDED, 0, 0);

        RiskAssessment assessment = service.score(record, CALM, FEATURES);

        assertThat(assessment.getScore()).isLessThan(40.0);
        assertThat(assessment.getRecommendation()).isEqualTo(RiskRecommendation.APPROVE);
    }

    @Test
    @DisplayName("A confirmed exclusion forces Reject even when the model is calm")
    void confirmedExclusionForcesReject() {
        VerificationRecord record = verification(NpiVerificationStatus.VALID,
                ExclusionStatus.EXCLUDED, 0, 0);

        RiskAssessment assessment = service.score(record, CALM, FEATURES);

        assertThat(assessment.getRecommendation()).isEqualTo(RiskRecommendation.REJECT);
        assertThat(assessment.getFlags()).contains("Confirmed OIG exclusion");
    }

    @Test
    @DisplayName("A malformed identifier forces Reject even when the model is calm")
    void malformedIdentifierForcesReject() {
        VerificationRecord record = verification(NpiVerificationStatus.INVALID_FORMAT,
                ExclusionStatus.NOT_EXCLUDED, 0, 0);

        RiskAssessment assessment = service.score(record, CALM, FEATURES);

        assertThat(assessment.getRecommendation()).isEqualTo(RiskRecommendation.REJECT);
    }

    @Test
    @DisplayName("A possible name match is routed to review rather than rejected outright")
    void possibleMatchIsReviewed() {
        VerificationRecord record = verification(NpiVerificationStatus.VALID,
                ExclusionStatus.POSSIBLE_MATCH, 0, 0);
        record.setExclusionMatchScore(0.94);

        RiskAssessment assessment = service.score(record, CALM, FEATURES);

        assertThat(assessment.getRecommendation()).isEqualTo(RiskRecommendation.REVIEW);
        assertThat(assessment.getFlags()).contains("Possible OIG name match");
    }

    @Test
    @DisplayName("Field discrepancies raise the score and are explained in the flags")
    void discrepanciesRaiseTheScore() {
        VerificationRecord clean = verification(NpiVerificationStatus.VALID,
                ExclusionStatus.NOT_EXCLUDED, 0, 0);
        VerificationRecord conflicted = verification(NpiVerificationStatus.VALID,
                ExclusionStatus.NOT_EXCLUDED, 3, 2);

        double cleanScore = service.score(clean, CALM, FEATURES).getScore();
        RiskAssessment conflictedAssessment = service.score(conflicted, CALM, FEATURES);

        assertThat(conflictedAssessment.getScore()).isGreaterThan(cleanScore);
        assertThat(conflictedAssessment.getFlags()).contains("3 fields differ");
    }

    @Test
    @DisplayName("An unreachable registry is recorded as uncertainty, not as a clean result")
    void unreachableRegistryIsRecorded() {
        VerificationRecord record = verification(NpiVerificationStatus.UNAVAILABLE,
                ExclusionStatus.NOT_EXCLUDED, 0, 0);

        RiskAssessment assessment = service.score(record, CALM, FEATURES);

        assertThat(assessment.getFlags()).contains("registry unreachable");
        assertThat(assessment.getScore()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("A fallback result is labelled so a reviewer knows it was not live")
    void fallbackResultIsLabelled() {
        VerificationRecord record = verification(NpiVerificationStatus.VALID,
                ExclusionStatus.NOT_EXCLUDED, 0, 0);
        record.setSource(VerificationSource.OFFLINE_FALLBACK);

        RiskAssessment assessment = service.score(record, CALM, FEATURES);

        assertThat(assessment.getFlags()).contains("offline registry snapshot");
    }

    @Test
    @DisplayName("A deactivated identifier is escalated to review despite a low score")
    void deactivatedIdentifierIsEscalated() {
        VerificationRecord record = verification(NpiVerificationStatus.DEACTIVATED,
                ExclusionStatus.NOT_EXCLUDED, 0, 0);

        RiskAssessment assessment = service.score(record, CALM, FEATURES);

        // The weighted score alone stays under the 40-point review threshold;
        // the escalation rule is what keeps this record in front of a person.
        assertThat(assessment.getScore()).isLessThan(40.0);
        assertThat(assessment.getRecommendation()).isEqualTo(RiskRecommendation.REVIEW);
    }

    @Test
    @DisplayName("Three field discrepancies are escalated to review despite a low score")
    void multipleDiscrepanciesAreEscalated() {
        VerificationRecord record = verification(NpiVerificationStatus.VALID,
                ExclusionStatus.NOT_EXCLUDED, 3, 0);

        RiskAssessment assessment = service.score(record, CALM, FEATURES);

        assertThat(assessment.getScore()).isLessThan(40.0);
        assertThat(assessment.getRecommendation()).isEqualTo(RiskRecommendation.REVIEW);
    }

    @Test
    @DisplayName("A single identity-field conflict is escalated to review")
    void highSeverityDiscrepancyIsEscalated() {
        VerificationRecord record = verification(NpiVerificationStatus.VALID,
                ExclusionStatus.NOT_EXCLUDED, 1, 0);
        record.setHighSeverityDiscrepancyCount(1);

        RiskAssessment assessment = service.score(record, CALM, FEATURES);

        assertThat(assessment.getRecommendation()).isEqualTo(RiskRecommendation.REVIEW);
    }

    @Test
    @DisplayName("A submission missing three or more expected fields is escalated to review")
    void sparseSubmissionIsEscalated() {
        // A document from which no provider name could be read was previously
        // recommended for approval on the strength of the identifier alone.
        // See code review CR-13.
        VerificationRecord record = verification(NpiVerificationStatus.VALID,
                ExclusionStatus.NOT_EXCLUDED, 0, 3);

        RiskAssessment assessment = service.score(record, CALM, FEATURES);

        assertThat(assessment.getScore()).isLessThan(40.0);
        assertThat(assessment.getRecommendation()).isEqualTo(RiskRecommendation.REVIEW);
        assertThat(assessment.getFlags()).contains("3 expected fields were absent");
    }

    @Test
    @DisplayName("The anomaly component alone cannot drive a Reject recommendation")
    void anomalyAloneDoesNotReject() {
        VerificationRecord record = verification(NpiVerificationStatus.VALID,
                ExclusionStatus.NOT_EXCLUDED, 0, 0);
        var alarmed = new AnomalyDetectionService.AnomalyResult(
                0.99, true, 0.9, "unusual combination", "test-model");

        RiskAssessment assessment = service.score(record, alarmed, FEATURES);

        // 0.99 of a 15 per cent weighting cannot reach the 70-point reject threshold.
        assertThat(assessment.getRecommendation()).isNotEqualTo(RiskRecommendation.REJECT);
    }

    /** Builds a verification record with the supplied outcomes. */
    private VerificationRecord verification(NpiVerificationStatus npiStatus,
                                            ExclusionStatus exclusionStatus,
                                            int discrepancies, int missingFields) {
        VerificationRecord record = new VerificationRecord();
        record.setNpiStatus(npiStatus);
        record.setExclusionStatus(exclusionStatus);
        record.setDiscrepancyCount(discrepancies);
        record.setMissingFieldCount(missingFields);
        record.setSource(VerificationSource.LIVE_API);
        record.setProvider(new Provider());
        return record;
    }
}
