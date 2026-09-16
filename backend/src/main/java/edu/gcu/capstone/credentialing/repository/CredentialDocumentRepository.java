package edu.gcu.capstone.credentialing.repository;

import edu.gcu.capstone.credentialing.domain.CredentialDocument;
import edu.gcu.capstone.credentialing.domain.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * CredentialDocumentRepository.java
 *
 * <p>Data-access interface for uploaded credential documents, including the
 * query used by the retention job to find binaries that are due for deletion.</p>
 */
public interface CredentialDocumentRepository extends JpaRepository<CredentialDocument, Long> {

    /** All documents belonging to one provider, newest first. */
    List<CredentialDocument> findByProviderIdOrderByUploadedAtDesc(Long providerId);

    /** Documents whose text extraction failed, i.e. the manual review queue. */
    List<CredentialDocument> findByStatus(DocumentStatus status);

    /**
     * Finds documents uploaded before the cut-off whose binary has not yet been
     * purged, so the retention policy can delete them.
     *
     * @param cutoff the oldest upload time that may still keep its binary
     * @return documents eligible for purging
     */
    List<CredentialDocument> findByUploadedAtBeforeAndPurgedAtIsNull(Instant cutoff);

    long countByStatus(DocumentStatus status);
}
