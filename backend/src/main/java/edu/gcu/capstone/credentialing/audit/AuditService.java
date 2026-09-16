package edu.gcu.capstone.credentialing.audit;

import edu.gcu.capstone.credentialing.domain.AuditLog;
import edu.gcu.capstone.credentialing.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AuditService.java
 *
 * <p>Writes the append-only audit trail. Every entry is written in its own
 * transaction ({@code REQUIRES_NEW}) so that a business transaction which later
 * rolls back still leaves its audit record behind. An audit trail that
 * disappears whenever an operation fails would be of no use precisely when it is
 * most needed.</p>
 *
 * <p>Recording never throws. A failure to write an audit row is logged and
 * swallowed, because losing an audit entry is preferable to failing the user's
 * operation because of it.</p>
 *
 * <p>This service supports functional requirement FR-11 (maintain an audit
 * trail of user and system actions).</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Records an action that is not tied to a specific record.
     *
     * @param username  who acted, may be null for anonymous attempts
     * @param role      the role held at the time, may be null
     * @param action    short verb, e.g. LOGIN_SUCCESS
     * @param module    system module, e.g. SECURITY
     * @param detail    what happened, not the data it happened to
     * @param outcome   SUCCESS or FAILURE
     * @param ipAddress the caller's address
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String username, String role, String action, String module,
                       String detail, String outcome, String ipAddress) {
        recordForEntity(username, role, action, module, null, null, detail, outcome, ipAddress);
    }

    /**
     * Records an action against a specific record.
     *
     * @param username   who acted
     * @param role       the role held at the time
     * @param action     short verb, e.g. DOCUMENT_UPLOAD
     * @param module     system module, e.g. INTAKE
     * @param entityType the type affected, e.g. PROVIDER
     * @param entityId   the identifier affected
     * @param detail     what happened
     * @param outcome    SUCCESS or FAILURE
     * @param ipAddress  the caller's address
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordForEntity(String username, String role, String action, String module,
                                String entityType, Long entityId, String detail,
                                String outcome, String ipAddress) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUsername(username);
            entry.setRole(role);
            entry.setAction(action);
            entry.setModule(module);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setDetail(truncate(detail));
            entry.setOutcome(outcome);
            entry.setIpAddress(ipAddress);
            repository.save(entry);
        } catch (Exception ex) {
            // Never let auditing break the operation being audited.
            log.error("Unable to write audit entry for action {}: {}", action, ex.toString());
        }
    }

    /** @return the most recent entries, newest first */
    @Transactional(readOnly = true)
    public List<AuditLog> recentEntries() {
        return repository.findTop200ByOrderByTimestampDesc();
    }

    /** Keeps a detail string inside the column width without failing the write. */
    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= 500 ? detail : detail.substring(0, 497) + "...";
    }
}
