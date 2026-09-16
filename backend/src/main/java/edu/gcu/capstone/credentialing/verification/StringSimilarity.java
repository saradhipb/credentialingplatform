package edu.gcu.capstone.credentialing.verification;

/**
 * StringSimilarity.java
 *
 * <p>Jaro-Winkler string similarity, used to compare provider names against the
 * OIG exclusion list and against registry records.</p>
 *
 * <p>Exact matching is not adequate for exclusion screening. A document may read
 * "Jon Smyth" where the exclusion list holds "John Smith", and an exclusion that
 * is missed because of one transposed character is a compliance failure. Equally,
 * matching too loosely would flag unrelated providers and destroy the reviewer's
 * trust in the screen. Jaro-Winkler is the standard choice for short personal
 * names because it weights agreement near the start of the string, which is
 * where names are most stable, and it handles transpositions explicitly.</p>
 *
 * <p>The similarity threshold is configuration, not code: see
 * {@code app.oig.name-match-threshold} in application.yml.</p>
 */
public final class StringSimilarity {

    /** Winkler's constant prefix weight; 0.1 is the value given in the original paper. */
    private static final double PREFIX_SCALE = 0.1;

    /** Maximum number of leading characters that receive the prefix bonus. */
    private static final int MAX_PREFIX = 4;

    private StringSimilarity() {
    }

    /**
     * Computes Jaro-Winkler similarity between two strings.
     *
     * @param left  first string, may be null
     * @param right second string, may be null
     * @return similarity in the range 0.0 (no similarity) to 1.0 (identical)
     */
    public static double jaroWinkler(String left, String right) {
        double jaro = jaro(left, right);
        if (jaro <= 0.0) {
            return 0.0;
        }
        int prefix = commonPrefixLength(left, right);
        return jaro + (prefix * PREFIX_SCALE * (1.0 - jaro));
    }

    /**
     * Computes the Jaro similarity, which combines the proportion of matching
     * characters with the number of transpositions between them.
     *
     * @param left  first string, may be null
     * @param right second string, may be null
     * @return similarity in the range 0.0 to 1.0
     */
    public static double jaro(String left, String right) {
        if (left == null || right == null) {
            return 0.0;
        }
        if (left.equals(right)) {
            return 1.0;
        }
        int lengthLeft = left.length();
        int lengthRight = right.length();
        if (lengthLeft == 0 || lengthRight == 0) {
            return 0.0;
        }

        // Characters further apart than this window are not considered matches.
        int window = Math.max(lengthLeft, lengthRight) / 2 - 1;
        if (window < 0) {
            window = 0;
        }

        boolean[] leftMatched = new boolean[lengthLeft];
        boolean[] rightMatched = new boolean[lengthRight];
        int matches = 0;

        for (int i = 0; i < lengthLeft; i++) {
            int start = Math.max(0, i - window);
            int end = Math.min(i + window + 1, lengthRight);
            for (int j = start; j < end; j++) {
                if (rightMatched[j] || left.charAt(i) != right.charAt(j)) {
                    continue;
                }
                leftMatched[i] = true;
                rightMatched[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) {
            return 0.0;
        }

        // Count how many matched characters appear in a different relative order.
        int transpositions = 0;
        int k = 0;
        for (int i = 0; i < lengthLeft; i++) {
            if (!leftMatched[i]) {
                continue;
            }
            while (!rightMatched[k]) {
                k++;
            }
            if (left.charAt(i) != right.charAt(k)) {
                transpositions++;
            }
            k++;
        }

        double m = matches;
        return ((m / lengthLeft) + (m / lengthRight) + ((m - transpositions / 2.0) / m)) / 3.0;
    }

    /**
     * Counts shared leading characters, capped at four.
     *
     * @param left  first string
     * @param right second string
     * @return the common prefix length, 0 to 4
     */
    private static int commonPrefixLength(String left, String right) {
        int limit = Math.min(MAX_PREFIX, Math.min(left.length(), right.length()));
        int prefix = 0;
        while (prefix < limit && left.charAt(prefix) == right.charAt(prefix)) {
            prefix++;
        }
        return prefix;
    }
}
