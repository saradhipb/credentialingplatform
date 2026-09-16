package edu.gcu.capstone.credentialing.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * AuditLog.java
 *
 * <p>Append-only record of every security-relevant action: sign-in attempts,
 * document uploads, verification runs, report exports, reviewer decisions and
 * administrative changes. Rows are never updated or deleted by application
 * code, which is what makes the log usable as compliance evidence.</p>
 *
 * <p>The {@code detail} column deliberately records what happened rather than
 * the content it happened to, so the audit trail does not become a second copy
 * of provider information.</p>
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_username", columnList = "username")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80)
    private String username;

    /** Role held by the user at the time of the action. */
    @Column(length = 20)
    private String role;

    /** Short verb describing the action, e.g. LOGIN_SUCCESS or DOCUMENT_UPLOAD. */
    @Column(length = 60, nullable = false)
    private String action;

    /** System module that handled the action, e.g. VERIFICATION or REPORTING. */
    @Column(length = 40)
    private String module;

    /** Type and identifier of the affected record, when one applies. */
    @Column(length = 40)
    private String entityType;

    private Long entityId;

    @Column(length = 500)
    private String detail;

    /** SUCCESS or FAILURE. */
    @Column(length = 20)
    private String outcome;

    @Column(length = 60)
    private String ipAddress;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    // ----- accessors ------------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
