package edu.gcu.capstone.credentialing.domain;

/**
 * ProviderStatus.java
 *
 * <p>Lifecycle state of a provider record inside the credentialing workflow.
 * The value is set by {@code CredentialingWorkflowService} once verification
 * and risk scoring have completed, and it drives the colour-coded status badge
 * shown on the Verification Dashboard.</p>
 */
public enum ProviderStatus {
    /** Created but not yet processed through verification. */
    PENDING,
    /** Verified with no exclusion, no NPI problem and an acceptable risk score. */
    ACTIVE,
    /** Verification completed but the record requires human review. */
    FLAGGED,
    /** A confirmed OIG exclusion or invalid NPI was found. */
    REJECTED,
    /** Automated processing could not complete; queued for manual handling. */
    MANUAL_REVIEW
}
