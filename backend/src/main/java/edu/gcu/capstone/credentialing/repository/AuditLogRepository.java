package edu.gcu.capstone.credentialing.repository;

import edu.gcu.capstone.credentialing.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * AuditLogRepository.java
 *
 * <p>Read and append access to the audit trail. No update or delete method is
 * declared, which keeps the log append-only at the application level.</p>
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop200ByOrderByTimestampDesc();

    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);

    List<AuditLog> findByModuleOrderByTimestampDesc(String module);
}
