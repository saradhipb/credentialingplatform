package edu.gcu.capstone.credentialing.domain;

/**
 * Role.java
 *
 * <p>Role-based access control roles. The access matrix defined in Milestone 1
 * is enforced by {@code AuthorizationFilter} using these values:</p>
 *
 * <pre>
 *   Role       View   Upload/Verify   Administer
 *   ADMIN      yes    yes             yes
 *   REVIEWER   yes    yes             no
 *   VIEWER     yes    no              no
 * </pre>
 */
public enum Role {
    /** Full access including user management and configuration. */
    ADMIN,
    /** May upload documents, run verification and record decisions. */
    REVIEWER,
    /** Read-only access to dashboards and reports. */
    VIEWER
}
