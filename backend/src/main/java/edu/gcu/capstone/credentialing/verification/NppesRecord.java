package edu.gcu.capstone.credentialing.verification;

import edu.gcu.capstone.credentialing.domain.NpiType;

/**
 * NppesRecord.java
 *
 * <p>The subset of a CMS NPPES registry record that the platform uses. Only the
 * fields needed to verify identity are mapped; the remainder of the registry
 * payload is retained verbatim on the verification record for audit purposes but
 * is never expanded into the domain model.</p>
 *
 * @param npi               the ten-digit identifier the registry returned
 * @param npiType           individual (Type 1) or organization (Type 2)
 * @param firstName         given name for a Type 1 record, else null
 * @param lastName          family name for a Type 1 record, else null
 * @param organizationName  legal business name for a Type 2 record, else null
 * @param status            registry status code; "A" denotes an active record
 * @param enumerationDate   date the identifier was issued, as returned
 * @param addressLine1      practice location street address
 * @param city              practice location city
 * @param state             practice location state
 * @param postalCode        practice location postal code
 * @param primaryTaxonomy   description of the primary taxonomy (specialty)
 * @param taxonomyLicense   license number attached to the primary taxonomy
 * @param rawJson           the registry payload exactly as received
 */
public record NppesRecord(
        String npi,
        NpiType npiType,
        String firstName,
        String lastName,
        String organizationName,
        String status,
        String enumerationDate,
        String addressLine1,
        String city,
        String state,
        String postalCode,
        String primaryTaxonomy,
        String taxonomyLicense,
        String rawJson) {

    /** @return true when the registry reports the identifier as active */
    public boolean isActive() {
        return status == null || "A".equalsIgnoreCase(status);
    }

    /**
     * @return the registry's name for the provider, using the organization name
     *         for Type 2 records and "last, first" for Type 1 records
     */
    public String displayName() {
        if (organizationName != null && !organizationName.isBlank()) {
            return organizationName;
        }
        String last = lastName == null ? "" : lastName;
        String first = firstName == null ? "" : firstName;
        return (last + ", " + first).trim();
    }

    /** @return the full practice address on one line, for side-by-side display */
    public String fullAddress() {
        StringBuilder builder = new StringBuilder();
        if (addressLine1 != null) {
            builder.append(addressLine1);
        }
        if (city != null) {
            builder.append(builder.length() > 0 ? ", " : "").append(city);
        }
        if (state != null) {
            builder.append(builder.length() > 0 ? ", " : "").append(state);
        }
        if (postalCode != null) {
            builder.append(' ').append(postalCode);
        }
        return builder.toString().trim();
    }
}
