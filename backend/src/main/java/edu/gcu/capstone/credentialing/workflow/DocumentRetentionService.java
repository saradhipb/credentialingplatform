package edu.gcu.capstone.credentialing.workflow;

import edu.gcu.capstone.credentialing.audit.AuditService;
import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import edu.gcu.capstone.credentialing.domain.CredentialDocument;
import edu.gcu.capstone.credentialing.domain.DocumentStatus;
import edu.gcu.capstone.credentialing.repository.CredentialDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * DocumentRetentionService.java
 *
 * <p>Enforces the data-minimisation commitment made in Milestone 3: an uploaded
 * document is kept only as long as processing and review require it.</p>
 *
 * <p>The job runs daily. It deletes the stored binary and marks the row
 * {@link DocumentStatus#PURGED}, leaving the extracted fields, the verification
 * evidence and the audit trail intact. The record of what was submitted and what
 * was concluded therefore survives, while the copy of the provider's document
 * does not.</p>
 */
@Service
public class DocumentRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRetentionService.class);

    private final CredentialDocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final ApplicationProperties properties;
    private final AuditService auditService;

    public DocumentRetentionService(CredentialDocumentRepository documentRepository,
                                    DocumentStorageService storageService,
                                    ApplicationProperties properties,
                                    AuditService auditService) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.properties = properties;
        this.auditService = auditService;
    }

    /**
     * Purges document binaries that have passed the retention window. Scheduled
     * daily at 02:00 local time, when no interactive use is expected.
     *
     * @return the number of documents purged, returned for the manual trigger
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public int purgeExpiredDocuments() {
        Instant cutoff = Instant.now().minus(
                properties.getStorage().getRetentionDays(), ChronoUnit.DAYS);

        List<CredentialDocument> expired =
                documentRepository.findByUploadedAtBeforeAndPurgedAtIsNull(cutoff);

        int purged = 0;
        for (CredentialDocument document : expired) {
            if (document.getStoredFilename() != null) {
                storageService.delete(document.getStoredFilename());
            }
            document.setPurgedAt(Instant.now());
            document.setStatus(DocumentStatus.PURGED);
            documentRepository.save(document);
            purged++;
        }

        if (purged > 0) {
            log.info("Retention policy purged {} stored document(s) older than {} days",
                    purged, properties.getStorage().getRetentionDays());
            auditService.record("system", "SYSTEM", "RETENTION_PURGE", "STORAGE",
                    "Purged " + purged + " stored document binaries past the retention window",
                    "SUCCESS", "localhost");
        }
        return purged;
    }
}
