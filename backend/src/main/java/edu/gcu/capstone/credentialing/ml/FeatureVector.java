package edu.gcu.capstone.credentialing.ml;

import java.util.Arrays;

/**
 * FeatureVector.java
 *
 * <p>The seven-dimensional numeric representation of one verification event that
 * the anomaly detector operates on. Every component is scaled to the range
 * 0.0-1.0, where a higher value always means "more concerning". Keeping the
 * features on a common scale and a common polarity means the isolation trees
 * split on comparable quantities and the resulting score can be explained to a
 * reviewer in plain language.</p>
 *
 * <p>The features are deliberately derived from verification evidence rather
 * than from provider demographics. Nothing in this vector encodes who a provider
 * is; it encodes only what the verification process observed.</p>
 */
public class FeatureVector {

    /** Number of dimensions; referenced by the tree builder and the tests. */
    public static final int DIMENSIONS = 7;

    /** Human-readable names, used for the explainability panel and the audit trail. */
    public static final String[] FEATURE_NAMES = {
            "NPI verification risk",
            "OIG exclusion risk",
            "Discrepancy volume",
            "Discrepancy severity",
            "Missing field ratio",
            "Extraction uncertainty",
            "Name similarity gap"
    };

    private final double[] values;

    /**
     * @param values exactly {@link #DIMENSIONS} values, each between 0.0 and 1.0
     * @throws IllegalArgumentException when the wrong number of values is supplied
     */
    public FeatureVector(double... values) {
        if (values == null || values.length != DIMENSIONS) {
            throw new IllegalArgumentException("A feature vector must hold exactly " + DIMENSIONS + " values");
        }
        this.values = new double[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            // Clamping guards the model against an out-of-range feature caused by
            // an unexpected input rather than by genuinely unusual data.
            this.values[i] = Math.max(0.0, Math.min(1.0, values[i]));
        }
    }

    /**
     * @param index dimension index, 0 to {@link #DIMENSIONS}-1
     * @return the value at that dimension
     */
    public double get(int index) {
        return values[index];
    }

    /** @return a defensive copy of the underlying values */
    public double[] toArray() {
        return Arrays.copyOf(values, values.length);
    }

    /**
     * Identifies the dimensions that contributed most to a high score, used to
     * turn a numeric anomaly score into an explanation.
     *
     * @param threshold minimum value for a feature to be considered a contributor
     * @return names of the contributing features, highest value first
     */
    public String[] dominantFeatures(double threshold) {
        return java.util.stream.IntStream.range(0, DIMENSIONS)
                .boxed()
                .filter(i -> values[i] >= threshold)
                .sorted((a, b) -> Double.compare(values[b], values[a]))
                .map(i -> FEATURE_NAMES[i])
                .toArray(String[]::new);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < DIMENSIONS; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append('"').append(FEATURE_NAMES[i]).append("\": ")
                    .append(String.format("%.3f", values[i]));
        }
        return builder.append('}').toString();
    }
}
