package edu.gcu.capstone.credentialing.ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * IsolationForest.java
 *
 * <p>An ensemble of {@link IsolationTree} instances that scores how unusual a
 * verification event is relative to the population it was trained on.</p>
 *
 * <p>The anomaly score for a point x is</p>
 * <pre>
 *     s(x, n) = 2 ^ ( -E[h(x)] / c(n) )
 * </pre>
 * <p>where E[h(x)] is the mean path length across the trees and c(n) is the
 * expected path length for a sample of size n. The score lies in (0, 1): values
 * near 1 indicate a point that was isolated unusually quickly, values near 0.5
 * indicate a point indistinguishable from the rest of the population.</p>
 *
 * <p>The forest is trained with a fixed random seed so that a given build of the
 * platform produces the same score for the same input on every run, which is a
 * practical requirement for a system whose outputs must be auditable.</p>
 */
public class IsolationForest {

    private final List<IsolationTree> trees = new ArrayList<>();
    private final int sampleSize;
    private final String modelVersion;

    /**
     * Trains a forest.
     *
     * @param trainingData     the population to learn as "normal"
     * @param numberOfTrees    number of trees in the ensemble
     * @param requestedSample  points drawn per tree; reduced when the data is smaller
     * @param seed             random seed, fixed so results are repeatable
     * @throws IllegalArgumentException when there is too little data to train on
     */
    public IsolationForest(List<FeatureVector> trainingData, int numberOfTrees,
                           int requestedSample, long seed) {
        if (trainingData == null || trainingData.size() < 8) {
            throw new IllegalArgumentException("Isolation Forest needs at least 8 training points");
        }
        this.sampleSize = Math.min(requestedSample, trainingData.size());

        // The height limit is the average depth of a random binary tree over the
        // sub-sample. Growing past it costs time without improving separation.
        int heightLimit = (int) Math.ceil(log2(this.sampleSize));

        Random random = new Random(seed);
        List<FeatureVector> shuffleBuffer = new ArrayList<>(trainingData);

        for (int i = 0; i < numberOfTrees; i++) {
            Collections.shuffle(shuffleBuffer, random);
            List<FeatureVector> subSample = new ArrayList<>(shuffleBuffer.subList(0, this.sampleSize));
            trees.add(IsolationTree.build(subSample, 0, heightLimit, random));
        }

        this.modelVersion = String.format("isolation-forest-%dt-%ds-seed%d",
                numberOfTrees, this.sampleSize, seed);
    }

    /**
     * Scores one point.
     *
     * @param point the vector to score
     * @return an anomaly score in the range 0.0-1.0, higher meaning more unusual
     */
    public double score(FeatureVector point) {
        double totalPathLength = 0.0;
        for (IsolationTree tree : trees) {
            totalPathLength += tree.pathLength(point, 0);
        }
        double meanPathLength = totalPathLength / trees.size();
        double normaliser = IsolationTree.averagePathLength(sampleSize);
        if (normaliser <= 0.0) {
            return 0.0;
        }
        return Math.pow(2.0, -meanPathLength / normaliser);
    }

    /**
     * Converts a score into a confidence figure for display, expressing how far
     * the score sits from the decision threshold. A score exactly on the
     * threshold yields low confidence, which is the honest reading.
     *
     * @param score     the anomaly score
     * @param threshold the configured decision threshold
     * @return confidence in the range 0.0-1.0
     */
    public static double confidence(double score, double threshold) {
        double distance = Math.abs(score - threshold);
        double maxDistance = Math.max(threshold, 1.0 - threshold);
        if (maxDistance <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, distance / maxDistance);
    }

    /** @return the number of trees in the ensemble */
    public int treeCount() {
        return trees.size();
    }

    /** @return the sub-sample size used per tree */
    public int sampleSize() {
        return sampleSize;
    }

    /** @return an identifier recorded on every assessment this model produces */
    public String modelVersion() {
        return modelVersion;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }
}
