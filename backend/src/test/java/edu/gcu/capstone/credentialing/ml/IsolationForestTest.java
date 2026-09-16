package edu.gcu.capstone.credentialing.ml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IsolationForestTest.java
 *
 * <p>Module tests for the anomaly detector. The forest is unsupervised, so the
 * meaningful assertions are relative rather than absolute: a point far from the
 * training population must score higher than a point drawn from the middle of
 * it, and the same input must produce the same score on every run, because an
 * auditable system cannot give a different answer to the same question twice.</p>
 */
class IsolationForestTest {

    private List<FeatureVector> cleanPopulation;

    @BeforeEach
    void setUp() {
        cleanPopulation = new TrainingDataGenerator().generateCleanPopulation(400, 42L);
    }

    @Test
    @DisplayName("A clearly unusual record scores higher than a typical one")
    void scoresOutliersHigher() {
        IsolationForest forest = new IsolationForest(cleanPopulation, 120, 128, 42L);

        FeatureVector typical = new FeatureVector(0.0, 0.0, 0.0, 0.0, 0.05, 0.08, 0.02);
        FeatureVector unusual = new FeatureVector(1.0, 1.0, 1.0, 1.0, 0.95, 0.90, 0.95);

        assertThat(forest.score(unusual)).isGreaterThan(forest.score(typical));
    }

    @Test
    @DisplayName("Training with a fixed seed produces identical scores on every run")
    void isRepeatable() {
        FeatureVector sample = new FeatureVector(0.85, 0.0, 0.4, 0.3, 0.6, 0.5, 0.7);

        double first = new IsolationForest(cleanPopulation, 120, 128, 42L).score(sample);
        double second = new IsolationForest(cleanPopulation, 120, 128, 42L).score(sample);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("Every score lies strictly between 0 and 1")
    void scoresStayInRange() {
        IsolationForest forest = new IsolationForest(cleanPopulation, 60, 64, 7L);

        for (FeatureVector point : cleanPopulation) {
            assertThat(forest.score(point)).isBetween(0.0, 1.0);
        }
        assertThat(forest.score(new FeatureVector(1, 1, 1, 1, 1, 1, 1))).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("Training is refused when there is too little data to learn from")
    void refusesInsufficientTrainingData() {
        assertThatThrownBy(() -> new IsolationForest(cleanPopulation.subList(0, 4), 50, 32, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("The model reports the configuration it was built with")
    void reportsItsConfiguration() {
        IsolationForest forest = new IsolationForest(cleanPopulation, 90, 100, 5L);

        assertThat(forest.treeCount()).isEqualTo(90);
        assertThat(forest.sampleSize()).isEqualTo(100);
        assertThat(forest.modelVersion()).contains("90t").contains("100s");
    }

    @Test
    @DisplayName("A feature vector must hold exactly seven values")
    void enforcesVectorWidth() {
        assertThatThrownBy(() -> new FeatureVector(0.1, 0.2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Out-of-range feature values are clamped rather than distorting the model")
    void clampsFeatureValues() {
        FeatureVector vector = new FeatureVector(-3.0, 4.0, 0.5, 0.5, 0.5, 0.5, 0.5);

        assertThat(vector.get(0)).isEqualTo(0.0);
        assertThat(vector.get(1)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("The dominant features of a high-scoring vector are reported for explanation")
    void namesDominantFeatures() {
        FeatureVector vector = new FeatureVector(0.95, 0.0, 0.1, 0.1, 0.2, 0.1, 0.8);

        assertThat(vector.dominantFeatures(0.45))
                .containsExactly("NPI verification risk", "Name similarity gap");
    }
}
