package edu.gcu.capstone.credentialing.ml;

import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnomalyDetectionServiceTest.java
 *
 * <p>Module tests that assert the detector meets the quality targets set in
 * Milestone 2: precision and recall of at least 0.85 on the labelled evaluation
 * set. Because the model trains from a fixed seed, these targets are a genuine
 * regression guard - a change to the features, the generator or the threshold
 * that degraded detection would fail the build rather than pass unnoticed.</p>
 */
class AnomalyDetectionServiceTest {

    /** Target set in the Milestone 2 algorithm design. */
    private static final double TARGET = 0.85;

    private AnomalyDetectionService service;

    @BeforeEach
    void setUp() {
        service = new AnomalyDetectionService(new ApplicationProperties(), new TrainingDataGenerator());
        service.trainModel();
    }

    @Test
    @DisplayName("Precision meets the 0.85 target")
    void meetsPrecisionTarget() {
        assertThat(service.getEvaluation().precision()).isGreaterThanOrEqualTo(TARGET);
    }

    @Test
    @DisplayName("Recall meets the 0.85 target")
    void meetsRecallTarget() {
        assertThat(service.getEvaluation().recall()).isGreaterThanOrEqualTo(TARGET);
    }

    @Test
    @DisplayName("F1 exceeds 0.85 and the whole evaluation set is accounted for")
    void reportsCoherentMetrics() {
        ModelEvaluation evaluation = service.getEvaluation();

        assertThat(evaluation.f1Score()).isGreaterThan(TARGET);
        assertThat(evaluation.sampleCount()).isEqualTo(400);
        assertThat(evaluation.accuracy()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("A record with every adverse signal is reported as anomalous and explained")
    void flagsAndExplainsAClearProblem() {
        var result = service.score(new FeatureVector(1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.9));

        assertThat(result.anomalous()).isTrue();
        assertThat(result.explanation()).contains("Flagged primarily on");
        assertThat(result.modelVersion()).isNotBlank();
    }

    @Test
    @DisplayName("A clean record is not reported as anomalous")
    void leavesCleanRecordsAlone() {
        var result = service.score(new FeatureVector(0.0, 0.0, 0.0, 0.0, 0.05, 0.08, 0.02));

        assertThat(result.anomalous()).isFalse();
        assertThat(result.score()).isLessThan(service.getThreshold());
    }

    @Test
    @DisplayName("Scoring the same record twice gives the same answer")
    void isRepeatable() {
        FeatureVector vector = new FeatureVector(0.8, 0.0, 0.5, 0.4, 0.3, 0.2, 0.6);

        assertThat(service.score(vector).score()).isEqualTo(service.score(vector).score());
    }
}
