package edu.gcu.capstone.credentialing.extraction;

import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * DataCleansingService.java
 *
 * <p>Normalises text recovered from documents so that differences of format,
 * spacing or letter case never masquerade as genuine discrepancies. This is the
 * data-cleansing subcomponent of the Extract Data module in the Milestone 1
 * decomposition, and it is deliberately applied to <em>both</em> sides of every
 * comparison: the document value and the registry value are cleansed with the
 * same rules before they are compared.</p>
 *
 * <p>Without this step a document reading "SMITH,  JOHN A." and a registry
 * record reading "John A Smith" would be reported as a mismatch, producing a
 * false anomaly that a reviewer would have to clear by hand.</p>
 */
@Service
public class DataCleansingService {

    /**
     * Collapses runs of whitespace, removes control characters, and trims.
     *
     * @param value raw text, may be null
     * @return normalised text, or null when the input held nothing
     */
    public String normaliseWhitespace(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Normalises a personal or organization name for storage and display.
     *
     * <p>Trailing punctuation left behind by recognition is stripped, internal
     * spacing is collapsed and the result is converted to title case so that
     * "SMITH" and "smith" become the same stored value.</p>
     *
     * @param name raw name text, may be null
     * @return a title-cased name, or null
     */
    public String normaliseName(String name) {
        String cleaned = normaliseWhitespace(name);
        if (cleaned == null) {
            return null;
        }
        cleaned = cleaned.replaceAll("[.,;:]+$", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        StringBuilder out = new StringBuilder(cleaned.length());
        boolean startOfWord = true;
        for (char c : cleaned.toCharArray()) {
            if (startOfWord) {
                out.append(Character.toUpperCase(c));
            } else {
                out.append(Character.toLowerCase(c));
            }
            // A hyphen or apostrophe also starts a new word: "O'Neill", "Smith-Jones".
            startOfWord = c == ' ' || c == '-' || c == '\'';
        }
        return restoreAcronyms(out.toString());
    }

    /**
     * Entity suffixes and professional credentials that are written in capitals
     * rather than title case. Without this step "SAGUARO VALLEY FAMILY CLINIC
     * LLC" would be stored as "Saguaro Valley Family Clinic Llc", which is not
     * how the organization writes its own name.
     */
    private static final java.util.Set<String> ALWAYS_UPPER_CASE = java.util.Set.of(
            "LLC", "L.L.C.", "INC", "INC.", "PC", "P.C.", "PA", "P.A.", "LLP", "PLLC",
            "LTD", "LP", "MD", "M.D.", "DO", "D.O.", "DDS", "DPM", "DMD", "NP", "PA-C",
            "RN", "LPN", "PT", "OT", "PHD", "II", "III", "IV");

    /**
     * Restores the capitalisation of entity suffixes and credentials that
     * title-casing would have flattened.
     *
     * @param titleCased a title-cased name
     * @return the same name with recognised acronyms in capitals
     */
    private String restoreAcronyms(String titleCased) {
        String[] words = titleCased.split(" ");
        for (int i = 0; i < words.length; i++) {
            if (ALWAYS_UPPER_CASE.contains(words[i].toUpperCase(Locale.ROOT))) {
                words[i] = words[i].toUpperCase(Locale.ROOT);
            }
        }
        return String.join(" ", words);
    }

    /**
     * Produces the canonical form used when two names are compared. Case,
     * punctuation, professional suffixes and word order are all removed, so
     * "Smith, John A., M.D." and "JOHN SMITH MD" reduce to the same key.
     *
     * @param name any name text, may be null
     * @return a comparison key, never null
     */
    public String comparisonKey(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(m\\.?d\\.?|d\\.?o\\.?|r\\.?n\\.?|n\\.?p\\.?|p\\.?a\\.?|ph\\.?d\\.?|"
                        + "d\\.?d\\.?s\\.?|jr|sr|ii|iii|iv)\\b", " ")
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        // Sorting the remaining tokens makes the key independent of name order.
        String[] tokens = cleaned.isEmpty() ? new String[0] : cleaned.split(" ");
        java.util.Arrays.sort(tokens);
        return String.join(" ", tokens);
    }

    /**
     * Reduces a state value to its two-letter USPS abbreviation where possible.
     *
     * @param state raw state text, may be null
     * @return an upper-case two-letter code, or null
     */
    public String normaliseState(String state) {
        String cleaned = normaliseWhitespace(state);
        if (cleaned == null) {
            return null;
        }
        cleaned = cleaned.replaceAll("[^A-Za-z ]", "").trim();
        if (cleaned.length() == 2) {
            return cleaned.toUpperCase(Locale.ROOT);
        }
        String abbreviation = StateAbbreviations.toCode(cleaned);
        return abbreviation != null ? abbreviation : cleaned.toUpperCase(Locale.ROOT);
    }

    /**
     * Normalises a postal code to five digits, discarding the ZIP+4 extension so
     * that "85017-1234" and "85017" compare as equal.
     *
     * @param postalCode raw postal code, may be null
     * @return a five-digit code, or null
     */
    public String normalisePostalCode(String postalCode) {
        String cleaned = normaliseWhitespace(postalCode);
        if (cleaned == null) {
            return null;
        }
        String digits = cleaned.replaceAll("[^0-9]", "");
        if (digits.length() >= 5) {
            return digits.substring(0, 5);
        }
        return digits.isEmpty() ? null : digits;
    }

    /**
     * Normalises a street address for comparison by expanding nothing but
     * removing punctuation and standardising case and spacing. Suffix
     * abbreviations are left as written because the registry and the document
     * are both cleansed the same way.
     *
     * @param address raw address text, may be null
     * @return a comparison-ready address, never null
     */
    public String normaliseAddress(String address) {
        if (address == null) {
            return "";
        }
        return address.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Cleans a license number by removing spaces and separators while preserving
     * the alphanumeric characters that identify it.
     *
     * @param licenseNumber raw license text, may be null
     * @return an upper-case identifier, or null
     */
    public String normaliseLicenseNumber(String licenseNumber) {
        String cleaned = normaliseWhitespace(licenseNumber);
        if (cleaned == null) {
            return null;
        }
        String compact = cleaned.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return compact.isEmpty() ? null : compact;
    }

    /**
     * Applies every rule above to an extraction result, in place.
     *
     * @param data parsed document data to cleanse
     * @return the same instance, cleansed, for convenient chaining
     */
    public ExtractedProviderData cleanse(ExtractedProviderData data) {
        data.setNpi(NpiValidator.normalise(data.getNpi()));
        if (data.getNpi().isEmpty()) {
            data.setNpi(null);
        }
        data.setNpiChecksumValid(data.getNpi() != null && NpiValidator.isValid(data.getNpi()));

        data.setFirstName(normaliseName(data.getFirstName()));
        data.setLastName(normaliseName(data.getLastName()));
        data.setOrganizationName(normaliseName(data.getOrganizationName()));
        data.setAddressLine1(normaliseWhitespace(data.getAddressLine1()));
        data.setCity(normaliseName(data.getCity()));
        data.setState(normaliseState(data.getState()));
        data.setPostalCode(normalisePostalCode(data.getPostalCode()));
        data.setLicenseNumber(normaliseLicenseNumber(data.getLicenseNumber()));
        data.setLicenseState(normaliseState(data.getLicenseState()));
        data.setTaxonomy(normaliseWhitespace(data.getTaxonomy()));
        return data;
    }
}
