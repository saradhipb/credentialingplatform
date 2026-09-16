package edu.gcu.capstone.credentialing.ml;

/**
 * ModelEvaluation.java
 *
 * <p>Classification quality of the anomaly detector measured against the
 * labelled evaluation set, expressed as the confusion-matrix counts and the
 * precision, recall and F1 figures derived from them.</p>
 *
 * <p>Milestone 2 sets targets of precision and recall at or above 0.85. Both are
 * reported because they trade against each other and the cost of each error is
 * different: a false negative lets a problem record through unexamined, while a
 * false positive adds avoidable work to a reviewer's queue.</p>
 *
 * @param truePositives  problem records correctly reported as anomalous
 * @param falsePositives clean records incorrectly reported as anomalous
 * @param trueNegatives  clean records correctly left unflagged
 * @param falseNegatives problem records missed
 * @param threshold      the decision threshold these figures were measured at
 */
public record ModelEvaluation(int truePositives, int falsePositives,
                              int trueNegatives, int falseNegatives,
                              double threshold) {

    /** @return proportion of flagged records that were genuinely problems */
    public double precision() {
        int flagged = truePositives + falsePositives;
        return flagged == 0 ? 0.0 : (double) truePositives / flagged;
    }

    /** @return proportion of genuine problems that were flagged */
    public double recall() {
        int actual = truePositives + falseNegatives;
        return actual == 0 ? 0.0 : (double) truePositives / actual;
    }

    /** @return the harmonic mean of precision and recall */
    public double f1Score() {
        double precision = precision();
        double recall = recall();
        return (precision + recall) == 0.0 ? 0.0 : 2.0 * precision * recall / (precision + recall);
    }

    /** @return proportion of all records classified correctly */
    public double accuracy() {
        int total = truePositives + falsePositives + trueNegatives + falseNegatives;
        return total == 0 ? 0.0 : (double) (truePositives + trueNegatives) / total;
    }

    /** @return total number of records evaluated */
    public int sampleCount() {
        return truePositives + falsePositives + trueNegatives + falseNegatives;
    }
}
