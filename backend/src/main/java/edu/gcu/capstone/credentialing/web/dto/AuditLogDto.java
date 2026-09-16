package edu.gcu.capstone.credentialing.web.dto;

/**
 * AuditLogDto.java
 *
 * <p>One row of the audit trail as presented in the administrator's screen and
 * in the exported audit report.</p>
 *
 * @param id         entry identifier
 * @param timestamp  ISO-8601 time of the action
 * @param username   who acted
 * @param role       the role held at the time
 * @param action     what was done
 * @param module     which module handled it
 * @param entityType the type of record affected
 * @param entityId   the record affected
 * @param detail     explanation of the action
 * @param outcome    SUCCESS or FAILURE
 * @param ipAddress  the caller's address
 */
public record AuditLogDto(Long id, String timestamp, String username, String role,
                          String action, String module, String entityType, Long entityId,
                          String detail, String outcome, String ipAddress) {
}
