package edu.gcu.capstone.credentialing.web;

import edu.gcu.capstone.credentialing.audit.AuditService;
import edu.gcu.capstone.credentialing.web.dto.AuditLogDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AuditController.java
 *
 * <p>Read access to the audit trail. The trail is readable by every role,
 * because transparency about what the platform did is not privileged
 * information, but no endpoint anywhere permits altering or deleting an entry.</p>
 *
 * <p>Endpoint: {@code GET /api/audit}.</p>
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;
    private final DtoMapper mapper;

    public AuditController(AuditService auditService, DtoMapper mapper) {
        this.auditService = auditService;
        this.mapper = mapper;
    }

    /**
     * Returns the most recent audit entries.
     *
     * @return up to 200 entries, newest first
     */
    @GetMapping
    public List<AuditLogDto> recent() {
        return auditService.recentEntries().stream().map(mapper::toAuditDto).toList();
    }
}
