package edu.gcu.capstone.credentialing.verification;

/**
 * Discrepancy.java
 *
 * <p>One field-level difference between the value read from a submitted document
 * and the value held by the authoritative registry. Discrepancies are serialised
 * to JSON on the verification record and rendered side by side in the Risk
 * Analysis screen, so a reviewer can see exactly what disagreed rather than only
 * that something did.</p>
 *
 * @param field         the field compared, e.g. "Provider name"
 * @param documentValue the cleansed value taken from the document
 * @param registryValue the cleansed value returned by the registry
 * @param severity      HIGH, MEDIUM or LOW, reflecting how strongly the field
 *                      bears on identity
 */
public record Discrepancy(String field, String documentValue, String registryValue, String severity) {

    /** Identity-defining fields: a mismatch here questions who the provider is. */
    public static final String SEVERITY_HIGH = "HIGH";

    /** Supporting fields: a mismatch is meaningful but often explainable. */
    public static final String SEVERITY_MEDIUM = "MEDIUM";

    /** Informational fields: routinely stale in one source or the other. */
    public static final String SEVERITY_LOW = "LOW";

    /** @return a numeric weight used by the risk scoring engine */
    public double weight() {
        return switch (severity) {
            case SEVERITY_HIGH -> 1.0;
            case SEVERITY_MEDIUM -> 0.6;
            default -> 0.3;
        };
    }
}
