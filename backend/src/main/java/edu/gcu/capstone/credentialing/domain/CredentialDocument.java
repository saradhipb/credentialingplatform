package edu.gcu.capstone.credentialing.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * CredentialDocument.java
 *
 * <p>One uploaded credential document (license, certification, identification
 * record) together with the text extracted from it and metadata describing how
 * that extraction was performed.</p>
 *
 * <p>The stored binary is deleted by {@code DocumentRetentionService} once the
 * configured retention window expires; the row survives with
 * {@link DocumentStatus#PURGED} so the audit trail stays complete.</p>
 */
@Entity
@Table(name = "credential_documents", indexes = {
        @Index(name = "idx_document_provider", columnList = "provider_id")
})
public class CredentialDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    /** Name as supplied by the uploading user, retained for display only. */
    @Column(length = 260, nullable = false)
    private String originalFilename;

    /** Randomised name under which the file is written to disk. */
    @Column(length = 120)
    private String storedFilename;

    @Column(length = 120)
    private String contentType;

    private long sizeBytes;

    /** Free-text classification supplied on the upload form, e.g. "State license". */
    @Column(length = 80)
    private String documentType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    /** Raw text produced by the extraction engine. */
    @Lob
    private String extractedText;

    /** Which engine produced the text: TESSERACT_OCR or PDF_TEXT_LAYER. */
    @Column(length = 40)
    private String extractionEngine;

    /** Heuristic 0.0-1.0 confidence in the extracted text. */
    private double extractionConfidence;

    /** Milliseconds spent extracting text, used for the performance metrics. */
    private long extractionMillis;

    @Column(length = 80)
    private String uploadedBy;

    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();

    /** Set when the stored binary is removed under the retention policy. */
    private Instant purgedAt;

    // ----- accessors ------------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getStoredFilename() { return storedFilename; }
    public void setStoredFilename(String storedFilename) { this.storedFilename = storedFilename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
    public String getExtractionEngine() { return extractionEngine; }
    public void setExtractionEngine(String extractionEngine) { this.extractionEngine = extractionEngine; }
    public double getExtractionConfidence() { return extractionConfidence; }
    public void setExtractionConfidence(double extractionConfidence) { this.extractionConfidence = extractionConfidence; }
    public long getExtractionMillis() { return extractionMillis; }
    public void setExtractionMillis(long extractionMillis) { this.extractionMillis = extractionMillis; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
    public Instant getPurgedAt() { return purgedAt; }
    public void setPurgedAt(Instant purgedAt) { this.purgedAt = purgedAt; }
}
