package edu.gcu.capstone.credentialing.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Provider.java
 *
 * <p>Core entity of the platform. One row represents one healthcare provider
 * being credentialed, holding the identifying fields extracted from submitted
 * documents together with the current workflow status.</p>
 *
 * <p>Relationships, as specified in the Milestone 2 data model:</p>
 * <ul>
 *   <li>one provider has many {@link CredentialDocument} records;</li>
 *   <li>one provider has many {@link VerificationRecord} records;</li>
 *   <li>one provider has many {@link RiskAssessment} records over time.</li>
 * </ul>
 *
 * <p>Only the fields required to verify a provider are stored. No clinical or
 * patient data is held anywhere in the schema.</p>
 */
@Entity
@Table(name = "providers", indexes = {
        @Index(name = "idx_provider_npi", columnList = "npi"),
        @Index(name = "idx_provider_status", columnList = "status")
})
public class Provider {

    /** Surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ten-digit National Provider Identifier as printed on the document. */
    @Column(length = 10)
    private String npi;

    /** Individual (Type 1) or organizational (Type 2) enumeration. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private NpiType npiType = NpiType.UNKNOWN;

    @Column(length = 120)
    private String firstName;

    @Column(length = 120)
    private String lastName;

    /** Populated instead of first/last name for Type 2 organizational providers. */
    @Column(length = 240)
    private String organizationName;

    @Column(length = 200)
    private String addressLine1;

    @Column(length = 120)
    private String city;

    @Column(length = 2)
    private String state;

    @Column(length = 10)
    private String postalCode;

    /** Professional license number read from the credential document. */
    @Column(length = 60)
    private String licenseNumber;

    @Column(length = 2)
    private String licenseState;

    /** Primary taxonomy (specialty) description, when available. */
    @Column(length = 160)
    private String taxonomy;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ProviderStatus status = ProviderStatus.PENDING;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CredentialDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<VerificationRecord> verifications = new ArrayList<>();

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RiskAssessment> riskAssessments = new ArrayList<>();

    /**
     * Convenience accessor that returns the organization name for Type 2
     * providers and "last, first" for Type 1 providers.
     *
     * @return a single display name, never null
     */
    @Transient
    public String getDisplayName() {
        if (organizationName != null && !organizationName.isBlank()) {
            return organizationName;
        }
        String last = lastName == null ? "" : lastName;
        String first = firstName == null ? "" : firstName;
        String combined = (last + ", " + first).trim();
        return combined.equals(",") || combined.isBlank() ? "(name not extracted)" : combined;
    }

    /** Refreshes the modification timestamp; called by JPA before every update. */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ----- accessors ------------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNpi() { return npi; }
    public void setNpi(String npi) { this.npi = npi; }
    public NpiType getNpiType() { return npiType; }
    public void setNpiType(NpiType npiType) { this.npiType = npiType; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public String getLicenseState() { return licenseState; }
    public void setLicenseState(String licenseState) { this.licenseState = licenseState; }
    public String getTaxonomy() { return taxonomy; }
    public void setTaxonomy(String taxonomy) { this.taxonomy = taxonomy; }
    public ProviderStatus getStatus() { return status; }
    public void setStatus(ProviderStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<CredentialDocument> getDocuments() { return documents; }
    public void setDocuments(List<CredentialDocument> documents) { this.documents = documents; }
    public List<VerificationRecord> getVerifications() { return verifications; }
    public void setVerifications(List<VerificationRecord> verifications) { this.verifications = verifications; }
    public List<RiskAssessment> getRiskAssessments() { return riskAssessments; }
    public void setRiskAssessments(List<RiskAssessment> riskAssessments) { this.riskAssessments = riskAssessments; }
}
