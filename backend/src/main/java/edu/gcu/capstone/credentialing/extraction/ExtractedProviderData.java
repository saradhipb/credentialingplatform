package edu.gcu.capstone.credentialing.extraction;

import edu.gcu.capstone.credentialing.domain.NpiType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ExtractedProviderData.java
 *
 * <p>The structured result of parsing one credential document. Alongside the
 * parsed fields it keeps a list of the fields that could not be found, which
 * feeds both the machine-learning feature vector and the "missing information"
 * component of the risk score.</p>
 */
public class ExtractedProviderData {

    private String npi;
    private NpiType npiType = NpiType.UNKNOWN;
    private String firstName;
    private String lastName;
    private String organizationName;
    private String addressLine1;
    private String city;
    private String state;
    private String postalCode;
    private String licenseNumber;
    private String licenseState;
    private String taxonomy;

    /** Names of the expected fields that were not present in the document. */
    private final List<String> missingFields = new ArrayList<>();

    /** Raw text captured for each field before cleansing, kept for traceability. */
    private final Map<String, String> rawMatches = new LinkedHashMap<>();

    /** True when the NPI read from the document passes the CMS check digit test. */
    private boolean npiChecksumValid;

    /**
     * Records that an expected field was absent.
     *
     * @param field field name, e.g. "licenseNumber"
     */
    public void addMissingField(String field) {
        if (!missingFields.contains(field)) {
            missingFields.add(field);
        }
    }

    /**
     * Stores the raw text matched for a field before normalisation.
     *
     * @param field field name
     * @param raw   text exactly as it appeared in the document
     */
    public void recordRaw(String field, String raw) {
        if (raw != null && !raw.isBlank()) {
            rawMatches.put(field, raw.strip());
        }
    }

    /** @return true when at least an identifier or a name was recovered */
    public boolean isUsable() {
        return (npi != null && !npi.isBlank())
                || (lastName != null && !lastName.isBlank())
                || (organizationName != null && !organizationName.isBlank());
    }

    // ----- accessors ------------------------------------------------------

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
    public List<String> getMissingFields() { return missingFields; }
    public Map<String, String> getRawMatches() { return rawMatches; }
    public boolean isNpiChecksumValid() { return npiChecksumValid; }
    public void setNpiChecksumValid(boolean npiChecksumValid) { this.npiChecksumValid = npiChecksumValid; }
}
