package edu.gcu.capstone.credentialing.ml;

import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AnomalyDetectionService.java
 *
 * <p>The intelligence layer of the architecture. It trains an
 * {@link IsolationForest} at application start-up, scores each verification
 * event, and measures its own detection quality against a labelled evaluation
 * set so the reported precision and recall are produced by the running system
 * rather than quoted from elsewhere.</p>
 *
 * <p>The role of this service is bounded on purpose. It never determines whether
 * a provider is excluded or whether an identifier is valid; those are answered
 * by the authoritative sources. It answers a narrower question: does this
 * combination of verification observations look unlike the population of clean
 * verifications? That is a prompt for review, not a credentialing decision.</p>
 *
 * <p>This service satisfies functional requirement FR-04 (detect anomalies using
 * machine learning).</p>
 */
@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    /** Size of the synthetic clean population the forest is trained on. */
    private static final int TRAINING_POPULATION = 600;

    /** Composition of the labelled set used to measure precision and recall. */
    private static final int EVAL_CLEAN = 300;
    private static final int EVAL_PROBLEM = 100;

    private final ApplicationProperties properties;
    private final TrainingDataGenerator dataGenerator;

    private IsolationForest forest;
    private ModelEvaluation evaluation;

    public AnomalyDetectionService(ApplicationProperties properties,
                                   TrainingDataGenerator dataGenerator) {
        this.properties = properties;
        this.dataGenerator = dataGenerator;
    }

    /** Trains and evaluates the model once, during application start-up. */
    @PostConstruct
    public void trainModel() {
        long seed = properties.getMl().getRandomSeed();

        List<FeatureVector> trainingData =
                dataGenerator.generateCleanPopulation(TRAINING_POPULATION, seed);

        forest = new IsolationForest(trainingData,
                properties.getMl().getTrees(),
                properties.getMl().getSampleSize(),
                seed);

        evaluation = evaluate();

        log.info("Anomaly detector trained: {} trees, sample size {}, threshold {}",
                forest.treeCount(), forest.sampleSize(), properties.getMl().getAnomalyThreshold());
        log.info("Detection quality on the labelled evaluation set: precision {}, recall {}, F1 {}",
                String.format("%.3f", evaluation.precision()),
                String.format("%.3f", evaluation.recall()),
                String.format("%.3f", evaluation.f1Score()));
    }

    /**
     * Scores one verification event.
     *
     * @param features the feature vector for the event
     * @return the score, the flag, the confidence and a plain-language explanation
     */
    public AnomalyResult score(FeatureVector features) {
        double score = forest.score(features);
        double threshold = properties.getMl().getAnomalyThreshold();
        boolean anomalous = score >= threshold;
        double confidence = IsolationForest.confidence(score, threshold);

        String explanation = anomalous
                ? buildExplanation(features)
                : "The combination of verification results is consistent with the reference population.";

        return new AnomalyResult(score, anomalous, confidence, explanation, forest.modelVersion());
    }

    /**
     * Names the observations that drove a high score, so a reviewer sees why the
     * record was flagged instead of only that it was.
     *
     * @param features the scored vector
     * @return a sentence naming the contributing features
     */
    private String buildExplanation(FeatureVector features) {
        String[] dominant = features.dominantFeatures(0.45);
        if (dominant.length == 0) {
            return "Flagged on the overall combination of signals rather than any single observation.";
        }
        return "Flagged primarily on: " + String.join("; ", dominant) + ".";
    }

    /**
     * Measures the trained model against the labelled evaluation set.
     *
     * @return the confusion matrix and derived metrics
     */
    private ModelEvaluation evaluate() {
        List<TrainingDataGenerator.LabelledSample> samples = dataGenerator.generateEvaluationSet(
                EVAL_CLEAN, EVAL_PROBLEM, properties.getMl().getRandomSeed() + 1);

        double threshold = properties.getMl().getAnomalyThreshold();
        int truePositives = 0;
        int falsePositives = 0;
        int trueNegatives = 0;
        int falseNegatives = 0;

        for (TrainingDataGenerator.LabelledSample sample : samples) {
            boolean predicted = forest.score(sample.features()) >= threshold;
            if (sample.anomalous() && predicted) {
                truePositives++;
            } else if (!sample.anomalous() && predicted) {
                falsePositives++;
            } else if (!sample.anomalous()) {
                trueNegatives++;
            } else {
                falseNegatives++;
            }
        }
        return new ModelEvaluation(truePositives, falsePositives, trueNegatives, falseNegatives, threshold);
    }

    /** @return the most recent evaluation, shown on the administrator's model panel */
    public ModelEvaluation getEvaluation() {
        return evaluation;
    }

    /** @return the identifier of the trained model, recorded on every assessment */
    public String getModelVersion() {
        return forest == null ? "untrained" : forest.modelVersion();
    }

    /** @return the configured decision threshold */
    public double getThreshold() {
        return properties.getMl().getAnomalyThreshold();
    }

    /**
     * Output of scoring one verification event.
     *
     * @param score        anomaly score in the range 0.0-1.0
     * @param anomalous    true when the score reached the decision threshold
     * @param confidence   how far the score sits from the threshold, 0.0-1.0
     * @param explanation  plain-language reason shown to the reviewer
     * @param modelVersion identifier of the model that produced the score
     */
    public record AnomalyResult(double score, boolean anomalous, double confidence,
                                String explanation, String modelVersion) {
    }
}
