package edu.gcu.capstone.credentialing.domain;

/**
 * VerificationSource.java
 *
 * <p>Records where a verification result came from. Every stored verification
 * carries this value so that a reviewer can always tell whether a result was
 * obtained from the live registry or from the bundled offline snapshot used
 * when the registry is unreachable.</p>
 */
public enum VerificationSource {
    /** Result returned by a live call to the external registry. */
    LIVE_API,
    /** Result read from the bundled offline snapshot after a failed live call. */
    OFFLINE_FALLBACK,
    /** No result could be obtained from either source. */
    NONE
}
