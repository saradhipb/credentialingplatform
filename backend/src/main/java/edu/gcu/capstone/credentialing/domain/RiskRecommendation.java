package edu.gcu.capstone.credentialing.domain;

/**
 * RiskRecommendation.java
 *
 * <p>Decision-support output of the risk scoring engine. The recommendation is
 * advisory: a credentialing specialist records the final determination, which
 * the platform captures in the audit log.</p>
 */
public enum RiskRecommendation {
    /** Low risk; no adverse finding. */
    APPROVE,
    /** Medium risk or unresolved discrepancies; route to a human reviewer. */
    REVIEW,
    /** High risk, typically a confirmed exclusion or invalid identifier. */
    REJECT
}
