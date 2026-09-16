package edu.gcu.capstone.credentialing.domain;

/**
 * NpiType.java
 *
 * <p>The two National Provider Identifier enumeration types defined by CMS:
 * Type 1 identifies an individual clinician and Type 2 identifies an
 * organization such as a group practice or hospital.</p>
 */
public enum NpiType {
    /** Individual provider (Type 1). */
    INDIVIDUAL,
    /** Organizational provider (Type 2). */
    ORGANIZATION,
    /** Type could not be determined from the document or the registry. */
    UNKNOWN
}
