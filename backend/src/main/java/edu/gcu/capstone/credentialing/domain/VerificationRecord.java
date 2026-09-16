package edu.gcu.capstone.credentialing.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * VerificationRecord.java
 *
 * <p>Evidence produced by checking one provider against the external
 * authoritative sources. Each row captures the NPPES result, the OIG exclusion
 * result, the field-level discrepancies found between the document and the
 * registry, and enough provenance to explain later how the conclusion was
 * reached.</p>
 *
 * <p>This record is the platform's primary evidence. The machine-learning
 * output stored in {@link RiskAssessment} is supporting analysis only and never
 * overrides what an authoritative source reports.</p>
 */
@Entity
@Table(name = "verification_records", indexes = {
        @Index(name = "idx_verification_provider", columnList = "provider_id")
})
public class VerificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private CredentialDocument document;

    // ----- NPPES (CMS National Provider Identifier registry) --------------

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private NpiVerificationStatus npiStatus = NpiVerificationStatus.MISSING;

    /** Provider name exactly as the registry returned it, for side-by-side display. */
    @Column(length = 240)
    private String registryName;

    @Column(length = 200)
    private String registryAddress;

    @Column(length = 160)
    private String registryTaxonomy;

    /** NPPES enumeration date, stored as text because the API returns a date string. */
    @Column(length = 30)
    private String registryEnumerationDate;

    // ----- OIG LEIE (exclusion screening) ---------------------------------

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private ExclusionStatus exclusionStatus = ExclusionStatus.NOT_EXCLUDED;

    @Column(length = 240)
    private String exclusionMatchedName;

    @Column(length = 30)
    private String exclusionDate;

    /** Statutory basis for the exclusion, e.g. "1128b4" (license revocation). */
    @Column(length = 40)
    private String exclusionType;

    /** Similarity score of the name match that produced a POSSIBLE_MATCH result. */
    private double exclusionMatchScore;

    // ----- Cross-source comparison ----------------------------------------

    /**
     * Field-level differences between the document and the registry, stored as
     * a JSON array so the dashboard can render them without a second table.
     */
    @Lob
    private String discrepanciesJson;

    private int discrepancyCount;

    /**
     * How many of those differences were in identity-defining fields. Tracked
     * separately because one high-severity conflict is more serious than
     * several low-severity ones, and the escalation rule in the scoring engine
     * depends on that distinction.
     */
    private int highSeverityDiscrepancyCount;

    private int missingFieldCount;

    // ----- Provenance ------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private VerificationSource source = VerificationSource.NONE;

    /** Raw registry payload, retained so a reviewer can audit the conclusion. */
    @Lob
    private String apiResponse;

    /** Human-readable note explaining fallbacks, retries or failures. */
    @Column(length = 500)
    private String processingNote;

    private long durationMs;

    @Column(nullable = false)
    private Instant verifiedAt = Instant.now();

    // ----- accessors ------------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public CredentialDocument getDocument() { return document; }
    public void setDocument(CredentialDocument document) { this.document = document; }
    public NpiVerificationStatus getNpiStatus() { return npiStatus; }
    public void setNpiStatus(NpiVerificationStatus npiStatus) { this.npiStatus = npiStatus; }
    public String getRegistryName() { return registryName; }
    public void setRegistryName(String registryName) { this.registryName = registryName; }
    public String getRegistryAddress() { return registryAddress; }
    public void setRegistryAddress(String registryAddress) { this.registryAddress = registryAddress; }
    public String getRegistryTaxonomy() { return registryTaxonomy; }
    public void setRegistryTaxonomy(String registryTaxonomy) { this.registryTaxonomy = registryTaxonomy; }
    public String getRegistryEnumerationDate() { return registryEnumerationDate; }
    public void setRegistryEnumerationDate(String registryEnumerationDate) { this.registryEnumerationDate = registryEnumerationDate; }
    public ExclusionStatus getExclusionStatus() { return exclusionStatus; }
    public void setExclusionStatus(ExclusionStatus exclusionStatus) { this.exclusionStatus = exclusionStatus; }
    public String getExclusionMatchedName() { return exclusionMatchedName; }
    public void setExclusionMatchedName(String exclusionMatchedName) { this.exclusionMatchedName = exclusionMatchedName; }
    public String getExclusionDate() { return exclusionDate; }
    public void setExclusionDate(String exclusionDate) { this.exclusionDate = exclusionDate; }
    public String getExclusionType() { return exclusionType; }
    public void setExclusionType(String exclusionType) { this.exclusionType = exclusionType; }
    public double getExclusionMatchScore() { return exclusionMatchScore; }
    public void setExclusionMatchScore(double exclusionMatchScore) { this.exclusionMatchScore = exclusionMatchScore; }
    public String getDiscrepanciesJson() { return discrepanciesJson; }
    public void setDiscrepanciesJson(String discrepanciesJson) { this.discrepanciesJson = discrepanciesJson; }
    public int getDiscrepancyCount() { return discrepancyCount; }
    public void setDiscrepancyCount(int discrepancyCount) { this.discrepancyCount = discrepancyCount; }
    public int getHighSeverityDiscrepancyCount() { return highSeverityDiscrepancyCount; }
    public void setHighSeverityDiscrepancyCount(int highSeverityDiscrepancyCount) { this.highSeverityDiscrepancyCount = highSeverityDiscrepancyCount; }
    public int getMissingFieldCount() { return missingFieldCount; }
    public void setMissingFieldCount(int missingFieldCount) { this.missingFieldCount = missingFieldCount; }
    public VerificationSource getSource() { return source; }
    public void setSource(VerificationSource source) { this.source = source; }
    public String getApiResponse() { return apiResponse; }
    public void setApiResponse(String apiResponse) { this.apiResponse = apiResponse; }
    public String getProcessingNote() { return processingNote; }
    public void setProcessingNote(String processingNote) { this.processingNote = processingNote; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
}
