package edu.gcu.capstone.credentialing.verification;

/**
 * LeieRecord.java
 *
 * <p>One row of the HHS OIG List of Excluded Individuals/Entities. The field
 * names follow the column headings published with the LEIE downloadable file so
 * that the bundled sample can be replaced by the full monthly extract without
 * any change to the parser.</p>
 *
 * @param lastName        surname of an excluded individual
 * @param firstName       given name of an excluded individual
 * @param middleName      middle name, often blank
 * @param businessName    name of an excluded entity, used instead of a person name
 * @param generalCategory general provider category as published
 * @param specialty       provider specialty as published
 * @param npi             identifier of the excluded party, "0000000000" when unknown
 * @param exclusionType   statutory basis, e.g. 1128a1 or 1128b4
 * @param exclusionDate   date the exclusion took effect, formatted yyyyMMdd
 * @param reinstatementDate date of reinstatement when one has occurred
 * @param state           state of the excluded party's address
 */
public record LeieRecord(
        String lastName,
        String firstName,
        String middleName,
        String businessName,
        String generalCategory,
        String specialty,
        String npi,
        String exclusionType,
        String exclusionDate,
        String reinstatementDate,
        String state) {

    /**
     * @return true when the exclusion is still in force, that is, no
     *         reinstatement date has been published
     */
    public boolean isActive() {
        return reinstatementDate == null || reinstatementDate.isBlank()
                || "00000000".equals(reinstatementDate);
    }

    /** @return the excluded party's name, entity name preferred when present */
    public String displayName() {
        if (businessName != null && !businessName.isBlank()) {
            return businessName;
        }
        String last = lastName == null ? "" : lastName;
        String first = firstName == null ? "" : firstName;
        return (last + ", " + first).trim();
    }

    /** @return true when the published NPI field holds a usable identifier */
    public boolean hasUsableNpi() {
        return npi != null && npi.matches("\\d{10}") && !"0000000000".equals(npi);
    }
}
