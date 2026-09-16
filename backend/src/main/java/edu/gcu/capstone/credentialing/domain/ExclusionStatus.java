package edu.gcu.capstone.credentialing.domain;

/**
 * ExclusionStatus.java
 *
 * <p>Outcome of screening a provider against the HHS OIG List of Excluded
 * Individuals/Entities (LEIE).</p>
 */
public enum ExclusionStatus {
    /** No LEIE record matched the provider's NPI or name. */
    NOT_EXCLUDED,
    /** The provider's NPI matched an active LEIE exclusion record. */
    EXCLUDED,
    /** A name matched closely enough to warrant review but the NPI did not. */
    POSSIBLE_MATCH,
    /** The exclusion list could not be loaded. */
    UNAVAILABLE
}
