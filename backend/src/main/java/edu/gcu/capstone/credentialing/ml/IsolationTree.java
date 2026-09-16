package edu.gcu.capstone.credentialing.ml;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * IsolationTree.java
 *
 * <p>A single tree of an Isolation Forest. The tree is grown by repeatedly
 * choosing a dimension at random and a split value at random between that
 * dimension's minimum and maximum in the current subset, until each point is
 * isolated or the height limit is reached.</p>
 *
 * <p>The insight the algorithm rests on is that anomalies are few and different,
 * so a random split is more likely to separate an anomalous point early. The
 * depth at which a point comes to rest is therefore a measure of how unusual it
 * is, and no labelled training data is required to obtain it. That property is
 * what makes the technique appropriate here: a credentialing prototype has no
 * corpus of confirmed fraudulent providers to learn from.</p>
 *
 * <p>Reference: Liu, Ting and Zhou (2008), "Isolation Forest",
 * <i>Proceedings of the Eighth IEEE International Conference on Data Mining</i>.</p>
 */
public class IsolationTree {

    /** Dimension this node splits on; -1 at a leaf. */
    private int splitDimension = -1;

    /** Threshold the node splits at; meaningless at a leaf. */
    private double splitValue;

    private IsolationTree left;
    private IsolationTree right;

    /** Number of points that reached this node; used for the leaf depth correction. */
    private int size;

    /** True when the node was not split further. */
    private boolean leaf;

    /**
     * Builds a tree from a sample of feature vectors.
     *
     * @param sample      the points to isolate
     * @param currentDepth depth of this node, 0 at the root
     * @param heightLimit  maximum depth to grow to
     * @param random       source of randomness, seeded for repeatability
     * @return the constructed subtree
     */
    public static IsolationTree build(List<FeatureVector> sample, int currentDepth,
                                      int heightLimit, Random random) {
        IsolationTree node = new IsolationTree();
        node.size = sample.size();

        // Stop when the point is isolated, the sample is degenerate, or the tree
        // has reached the height at which further splitting adds no information.
        if (currentDepth >= heightLimit || sample.size() <= 1) {
            node.leaf = true;
            return node;
        }

        // Choose a dimension that actually varies; a constant dimension cannot
        // separate anything, and repeatedly selecting one would waste depth.
        List<Integer> usableDimensions = new ArrayList<>();
        for (int dimension = 0; dimension < FeatureVector.DIMENSIONS; dimension++) {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (FeatureVector point : sample) {
                min = Math.min(min, point.get(dimension));
                max = Math.max(max, point.get(dimension));
            }
            if (max - min > 1e-9) {
                usableDimensions.add(dimension);
            }
        }
        if (usableDimensions.isEmpty()) {
            node.leaf = true;
            return node;
        }

        int dimension = usableDimensions.get(random.nextInt(usableDimensions.size()));
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (FeatureVector point : sample) {
            min = Math.min(min, point.get(dimension));
            max = Math.max(max, point.get(dimension));
        }

        node.splitDimension = dimension;
        node.splitValue = min + random.nextDouble() * (max - min);

        List<FeatureVector> leftSample = new ArrayList<>();
        List<FeatureVector> rightSample = new ArrayList<>();
        for (FeatureVector point : sample) {
            if (point.get(dimension) < node.splitValue) {
                leftSample.add(point);
            } else {
                rightSample.add(point);
            }
        }

        // A split that separates nothing would recurse forever; treat it as a leaf.
        if (leftSample.isEmpty() || rightSample.isEmpty()) {
            node.leaf = true;
            node.splitDimension = -1;
            return node;
        }

        node.left = build(leftSample, currentDepth + 1, heightLimit, random);
        node.right = build(rightSample, currentDepth + 1, heightLimit, random);
        return node;
    }

    /**
     * Measures how deep a point falls in this tree.
     *
     * <p>When the point stops at a leaf that still holds several points, the
     * average path length of a random binary search tree of that size is added,
     * which corrects for the height limit cutting the tree short.</p>
     *
     * @param point        the vector to place
     * @param currentDepth depth of this node
     * @return the corrected path length
     */
    public double pathLength(FeatureVector point, int currentDepth) {
        if (leaf || splitDimension < 0) {
            return currentDepth + averagePathLength(size);
        }
        return point.get(splitDimension) < splitValue
                ? left.pathLength(point, currentDepth + 1)
                : right.pathLength(point, currentDepth + 1);
    }

    /**
     * Average path length of an unsuccessful search in a binary search tree of
     * {@code n} nodes, written c(n) in the Isolation Forest literature. It is the
     * normalising constant that turns a raw depth into a comparable score.
     *
     * @param n number of points
     * @return the expected path length, 0 when n is 1 or less
     */
    public static double averagePathLength(int n) {
        if (n <= 1) {
            return 0.0;
        }
        if (n == 2) {
            return 1.0;
        }
        final double eulerMascheroni = 0.5772156649015329;
        double harmonic = Math.log(n - 1.0) + eulerMascheroni;
        return 2.0 * harmonic - (2.0 * (n - 1.0) / n);
    }
}
