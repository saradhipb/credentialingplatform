package edu.gcu.capstone.credentialing.ml;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TrainingDataGenerator.java
 *
 * <p>Produces the synthetic population the anomaly detector is trained and
 * evaluated on.</p>
 *
 * <p>Synthetic data is used deliberately. No corpus of real credentialing
 * outcomes was available for this project, and using real provider documents to
 * train a model would place identifiable information in the model's training
 * set for no benefit to the prototype. Because every feature is a bounded,
 * well-understood quantity derived from the verification process, a generator
 * that encodes the expected distribution of those quantities is a defensible
 * stand-in, and its limitations are stated plainly in the Milestone 3 document
 * rather than hidden.</p>
 *
 * <p>Two populations are produced:</p>
 * <ul>
 *   <li><b>Clean records.</b> A verified provider with a valid identifier, no
 *       exclusion, and at most incidental formatting differences.</li>
 *   <li><b>Problem records.</b> Providers with an exclusion hit, an unverifiable
 *       identifier, several field conflicts, or poor extraction quality.</li>
 * </ul>
 *
 * <p>The forest is trained on the clean population only, which is what the
 * algorithm expects: it learns the shape of normal and reports distance from it.
 * The labelled mixture is used solely to measure precision and recall.</p>
 */
@Component
public class TrainingDataGenerator {

    /**
     * Builds the "normal" population used to train the forest.
     *
     * @param count how many vectors to generate
     * @param seed  random seed, fixed so training is repeatable
     * @return the generated clean population
     */
    public List<FeatureVector> generateCleanPopulation(int count, long seed) {
        Random random = new Random(seed);
        List<FeatureVector> population = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            population.add(cleanRecord(random));
        }
        return population;
    }

    /**
     * Builds a labelled evaluation set used to measure detection quality.
     *
     * @param cleanCount   number of clean records
     * @param problemCount number of problem records
     * @param seed         random seed
     * @return labelled samples in generation order
     */
    public List<LabelledSample> generateEvaluationSet(int cleanCount, int problemCount, long seed) {
        Random random = new Random(seed);
        List<LabelledSample> samples = new ArrayList<>(cleanCount + problemCount);
        for (int i = 0; i < cleanCount; i++) {
            samples.add(new LabelledSample(cleanRecord(random), false));
        }
        for (int i = 0; i < problemCount; i++) {
            samples.add(new LabelledSample(problemRecord(random, i), true));
        }
        return samples;
    }

    /**
     * Generates one record representing a provider who verifies cleanly: a valid
     * identifier, no exclusion, occasional minor address formatting differences
     * and good extraction quality.
     */
    private FeatureVector cleanRecord(Random random) {
        double npiRisk = random.nextDouble() < 0.04 ? 0.45 : 0.0;      // Occasional registry outage.
        double exclusionRisk = 0.0;
        double discrepancyVolume = random.nextDouble() < 0.35
                ? random.nextDouble() * 0.20 : 0.0;                     // At most one minor conflict.
        double discrepancySeverity = discrepancyVolume * (0.2 + random.nextDouble() * 0.2);
        double missingRatio = random.nextDouble() * 0.20;               // A field or two absent.
        double extractionUncertainty = 0.02 + random.nextDouble() * 0.15;
        double nameGap = random.nextDouble() * 0.10;
        return new FeatureVector(npiRisk, exclusionRisk, discrepancyVolume,
                discrepancySeverity, missingRatio, extractionUncertainty, nameGap);
    }

    /**
     * Generates one record representing a provider whose verification produced a
     * material problem. The variant index cycles through the distinct failure
     * shapes the platform is expected to catch, so the evaluation set is not
     * dominated by a single easy case.
     */
    private FeatureVector problemRecord(Random random, int variant) {
        return switch (variant % 4) {
            // Confirmed or probable exclusion.
            case 0 -> new FeatureVector(
                    random.nextDouble() * 0.3,
                    0.75 + random.nextDouble() * 0.25,
                    random.nextDouble() * 0.4,
                    random.nextDouble() * 0.4,
                    random.nextDouble() * 0.3,
                    0.05 + random.nextDouble() * 0.2,
                    random.nextDouble() * 0.3);
            // Identifier missing, malformed, or not present in the registry.
            case 1 -> new FeatureVector(
                    0.85 + random.nextDouble() * 0.15,
                    0.0,
                    random.nextDouble() * 0.3,
                    random.nextDouble() * 0.3,
                    0.4 + random.nextDouble() * 0.4,
                    0.1 + random.nextDouble() * 0.3,
                    0.4 + random.nextDouble() * 0.4);
            // Document and registry disagree across several fields.
            case 2 -> new FeatureVector(
                    random.nextDouble() * 0.2,
                    0.0,
                    0.65 + random.nextDouble() * 0.35,
                    0.60 + random.nextDouble() * 0.40,
                    random.nextDouble() * 0.3,
                    0.1 + random.nextDouble() * 0.3,
                    0.55 + random.nextDouble() * 0.45);
            // Poor quality scan: little extracted, low confidence, much missing.
            default -> new FeatureVector(
                    0.3 + random.nextDouble() * 0.4,
                    0.0,
                    random.nextDouble() * 0.3,
                    random.nextDouble() * 0.3,
                    0.6 + random.nextDouble() * 0.4,
                    0.6 + random.nextDouble() * 0.4,
                    0.5 + random.nextDouble() * 0.5);
        };
    }

    /**
     * One evaluation sample and its ground-truth label.
     *
     * @param features  the vector
     * @param anomalous true when the record was generated as a problem record
     */
    public record LabelledSample(FeatureVector features, boolean anomalous) {
    }
}
