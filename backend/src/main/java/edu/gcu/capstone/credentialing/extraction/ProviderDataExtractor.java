package edu.gcu.capstone.credentialing.extraction;

import edu.gcu.capstone.credentialing.domain.NpiType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ProviderDataExtractor.java
 *
 * <p>Turns the free text produced by the extraction engines into the structured
 * fields the verification layer needs. Credential documents are semi-structured:
 * they are laid out as labelled lines rather than as a fixed record, so the
 * parser is label-driven, with a positional fallback for the fields that are
 * most often printed without a label.</p>
 *
 * <p>Each field is searched for in order of reliability. The NPI, for example,
 * is first sought next to an "NPI" label; if no label is present the text is
 * scanned for any ten-digit number that satisfies the CMS check digit, which
 * avoids mistaking a fax number or a license number for an identifier.</p>
 *
 * <p>Every field that cannot be located is recorded on the result, because the
 * count of missing fields is itself a risk signal rather than a silent
 * omission.</p>
 */
@Service
public class ProviderDataExtractor {

    // ---- Label-driven patterns. CASE_INSENSITIVE throughout because documents
    // ---- vary between title case, upper case and sentence case. --------------

    /** "NPI: 1234567893", "NPI Number - 1234567893", "National Provider Identifier 1234567893". */
    private static final Pattern NPI_LABELLED = Pattern.compile(
            "(?:national\\s+provider\\s+identifier|npi(?:\\s*(?:number|no\\.?|#))?)\\s*[:\\-#]?\\s*"
                    + "([0-9OoIl][0-9OoIl\\s\\-]{8,14}[0-9OoIl])",
            Pattern.CASE_INSENSITIVE);

    /** Any bare ten-digit run, used only when no labelled NPI was found. */
    private static final Pattern TEN_DIGIT_RUN = Pattern.compile("\\b(\\d{10})\\b");

    private static final Pattern ORGANIZATION = Pattern.compile(
            "(?:organization\\s+name|legal\\s+business\\s+name|organization\\s+name|entity\\s+name|facility)"
                    + "\\s*[:\\-]?\\s*(.+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FULL_NAME = Pattern.compile(
            "(?:provider\\s+name|practitioner\\s+name|licensee|name\\s+of\\s+provider|name)"
                    + "\\s*[:\\-]\\s*(.+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LAST_NAME = Pattern.compile(
            "(?:last\\s+name|surname|family\\s+name)\\s*[:\\-]?\\s*(.+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern FIRST_NAME = Pattern.compile(
            "(?:first\\s+name|given\\s+name)\\s*[:\\-]?\\s*(.+)", Pattern.CASE_INSENSITIVE);

    /**
     * License number. The three alternatives exist to keep the match anchored to
     * a real label:
     * <ol>
     *   <li>"Licence Number: X", "License No. X", "License #: X" - the keyword is
     *       present, so the separator may be omitted;</li>
     *   <li>"Licence: X" - no keyword, so a colon or dash is required;</li>
     *   <li>"Lic # X" - the abbreviation, which requires the hash.</li>
     * </ol>
     *
     * <p>The leading look-behind is what makes this safe. An earlier version
     * allowed a bare {@code lic} alternative with an optional separator, which
     * matched the letters L-I-C inside ordinary words: "STATE LICENSING BOARD"
     * yielded a license number of "ENSING" and "CREDENTIALING APPLICATION"
     * yielded "ATION". Both were then compared against the registry and
     * reported as discrepancies, inflating the risk score on documents that
     * had nothing wrong with them.</p>
     */
    private static final Pattern LICENSE_NUMBER = Pattern.compile(
            "(?<![A-Za-z])(?:"
                    + "licen[sc]e\\s*(?:number|no\\.?|#)\\s*[:\\-]?\\s*"
                    + "|licen[sc]e\\s*[:\\-]\\s*"
                    + "|lic\\.?\\s*#\\s*:?\\s*"
                    + ")([A-Za-z0-9\\-]{4,20})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LICENSE_STATE = Pattern.compile(
            "(?:licen[sc]e\\s+state|state\\s+of\\s+licensure|issuing\\s+state)\\s*[:\\-]?\\s*([A-Za-z ]{2,30})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ADDRESS = Pattern.compile(
            "(?:practice\\s+address|business\\s+address|mailing\\s+address|address)\\s*[:\\-]\\s*(.+)",
            Pattern.CASE_INSENSITIVE);

    /** "Phoenix, AZ 85017" or "Phoenix AZ 85017-1234". */
    private static final Pattern CITY_STATE_ZIP = Pattern.compile(
            "([A-Za-z][A-Za-z .'\\-]{1,40}?)\\s*,?\\s+([A-Za-z]{2})\\s+(\\d{5}(?:-\\d{4})?)\\b");

    private static final Pattern TAXONOMY = Pattern.compile(
            "(?:taxonomy|primary\\s+taxonomy|specialty|speciality)\\s*[:\\-]?\\s*(.+)",
            Pattern.CASE_INSENSITIVE);

    /** "Entity Type: 1", "NPI Type: Type 2", "Enumeration Type: NPI-1". */
    private static final Pattern ENTITY_TYPE = Pattern.compile(
            "(?:entity\\s+type|npi\\s+type|enumeration\\s+type)\\s*[:\\-]?\\s*(?:type\\s*|npi-?)?([12])",
            Pattern.CASE_INSENSITIVE);

    /** Fields the platform expects to find; anything absent is reported. */
    private static final List<String> EXPECTED_FIELDS =
            List.of("npi", "name", "addressLine1", "city", "state", "postalCode", "licenseNumber");

    private final DataCleansingService cleansingService;

    public ProviderDataExtractor(DataCleansingService cleansingService) {
        this.cleansingService = cleansingService;
    }

    /**
     * Parses provider fields out of extracted document text.
     *
     * @param text text produced by the extraction engine; may be null or empty
     * @return populated, cleansed data with a list of fields that were missing
     */
    public ExtractedProviderData extract(String text) {
        ExtractedProviderData data = new ExtractedProviderData();
        if (text == null || text.isBlank()) {
            EXPECTED_FIELDS.forEach(data::addMissingField);
            return data;
        }

        extractNpi(text, data);
        extractNames(text, data);
        extractAddress(text, data);
        extractLicense(text, data);

        firstGroup(TAXONOMY, text).ifPresent(value -> {
            data.setTaxonomy(value);
            data.recordRaw("taxonomy", value);
        });

        determineEntityType(text, data);
        cleansingService.cleanse(data);
        recordMissingFields(data);
        return data;
    }

    /** Locates the NPI, preferring a labelled value and validating the checksum. */
    private void extractNpi(String text, ExtractedProviderData data) {
        Matcher labelled = NPI_LABELLED.matcher(text);
        if (labelled.find()) {
            String raw = labelled.group(1);
            String candidate = NpiValidator.normalise(raw);
            if (candidate.length() >= 10) {
                data.setNpi(candidate.substring(0, 10));
                data.recordRaw("npi", raw);
                return;
            }
        }

        // No usable labelled value: accept the first bare ten-digit number whose
        // check digit is correct, which filters out phone and fax numbers.
        Matcher bare = TEN_DIGIT_RUN.matcher(text);
        while (bare.find()) {
            String candidate = bare.group(1);
            if (NpiValidator.isValid(candidate)) {
                data.setNpi(candidate);
                data.recordRaw("npi", candidate);
                return;
            }
        }

        // Fall back to the first labelled value even if it failed validation, so
        // that the reviewer sees the number that was actually printed.
        if (labelled.reset().find()) {
            String candidate = NpiValidator.normalise(labelled.group(1));
            if (!candidate.isEmpty()) {
                data.setNpi(candidate);
                data.recordRaw("npi", labelled.group(1));
            }
        }
    }

    /** Locates organization, full, first and last name fields. */
    private void extractNames(String text, ExtractedProviderData data) {
        firstGroup(ORGANIZATION, text).ifPresent(value -> {
            data.setOrganizationName(value);
            data.recordRaw("organizationName", value);
        });

        firstGroup(LAST_NAME, text).ifPresent(value -> {
            data.setLastName(value);
            data.recordRaw("lastName", value);
        });
        firstGroup(FIRST_NAME, text).ifPresent(value -> {
            data.setFirstName(value);
            data.recordRaw("firstName", value);
        });

        // The bare "name" alternative in FULL_NAME also matches the label
        // "Organization Name:", so for an organizational submission it would
        // split the business name into a first and last name and store both.
        // An organization has no personal name, so the pattern is skipped once
        // one has been found.
        if (data.getOrganizationName() == null
                && data.getLastName() == null && data.getFirstName() == null) {
            firstGroup(FULL_NAME, text).ifPresent(value -> {
                data.recordRaw("providerName", value);
                splitFullName(value, data);
            });
        }
    }

    /**
     * Splits a single name string into first and last name.
     *
     * <p>Two printed conventions are handled: "Smith, John A." where the surname
     * precedes a comma, and "John A. Smith" where the surname is last. Trailing
     * professional suffixes such as "M.D." are removed first so they are not
     * mistaken for the surname.</p>
     *
     * @param fullName the matched name text
     * @param data     result object to populate
     */
    private void splitFullName(String fullName, ExtractedProviderData data) {
        String cleaned = fullName.replaceAll("(?i),?\\s*\\b(m\\.?d\\.?|d\\.?o\\.?|r\\.?n\\.?|n\\.?p\\.?|"
                + "p\\.?a\\.?|ph\\.?d\\.?|d\\.?d\\.?s\\.?)\\b\\.?", "").trim();

        if (cleaned.contains(",")) {
            String[] parts = cleaned.split(",", 2);
            data.setLastName(parts[0].trim());
            data.setFirstName(parts.length > 1 ? parts[1].trim() : null);
            return;
        }

        String[] tokens = cleaned.split("\\s+");
        if (tokens.length == 1) {
            data.setLastName(tokens[0]);
        } else if (tokens.length > 1) {
            data.setLastName(tokens[tokens.length - 1]);
            data.setFirstName(tokens[0]);
        }
    }

    /** Locates the street address and the city/state/postal code line. */
    private void extractAddress(String text, ExtractedProviderData data) {
        firstGroup(ADDRESS, text).ifPresent(value -> {
            // The matched line may already contain the city, state and ZIP.
            Matcher inline = CITY_STATE_ZIP.matcher(value);
            if (inline.find()) {
                data.setAddressLine1(value.substring(0, inline.start()).replaceAll("[,\\s]+$", ""));
                data.setCity(inline.group(1));
                data.setState(inline.group(2));
                data.setPostalCode(inline.group(3));
            } else {
                data.setAddressLine1(value);
            }
            data.recordRaw("address", value);
        });

        if (data.getCity() == null) {
            Matcher matcher = CITY_STATE_ZIP.matcher(text);
            if (matcher.find()) {
                data.setCity(matcher.group(1));
                data.setState(matcher.group(2));
                data.setPostalCode(matcher.group(3));
                data.recordRaw("cityStateZip", matcher.group());
            }
        }
    }

    /** Locates the license number and issuing state. */
    private void extractLicense(String text, ExtractedProviderData data) {
        firstGroup(LICENSE_NUMBER, text).ifPresent(value -> {
            data.setLicenseNumber(value);
            data.recordRaw("licenseNumber", value);
        });
        firstGroup(LICENSE_STATE, text).ifPresent(value -> {
            data.setLicenseState(value);
            data.recordRaw("licenseState", value);
        });
    }

    /**
     * Determines whether the document describes an individual or an organization,
     * preferring an explicit entity type and otherwise inferring from which name
     * fields were populated.
     */
    private void determineEntityType(String text, ExtractedProviderData data) {
        Matcher matcher = ENTITY_TYPE.matcher(text);
        if (matcher.find()) {
            data.setNpiType("1".equals(matcher.group(1)) ? NpiType.INDIVIDUAL : NpiType.ORGANIZATION);
            data.recordRaw("entityType", matcher.group());
            return;
        }
        if (data.getOrganizationName() != null) {
            data.setNpiType(NpiType.ORGANIZATION);
        } else if (data.getLastName() != null) {
            data.setNpiType(NpiType.INDIVIDUAL);
        }
    }

    /** Records every expected field that the parser could not populate. */
    private void recordMissingFields(ExtractedProviderData data) {
        if (isBlank(data.getNpi())) {
            data.addMissingField("npi");
        }
        if (isBlank(data.getLastName()) && isBlank(data.getOrganizationName())) {
            data.addMissingField("name");
        }
        if (isBlank(data.getAddressLine1())) {
            data.addMissingField("addressLine1");
        }
        if (isBlank(data.getCity())) {
            data.addMissingField("city");
        }
        if (isBlank(data.getState())) {
            data.addMissingField("state");
        }
        if (isBlank(data.getPostalCode())) {
            data.addMissingField("postalCode");
        }
        if (isBlank(data.getLicenseNumber())) {
            data.addMissingField("licenseNumber");
        }
    }

    /**
     * Returns the first capture group of the first line that matches a pattern.
     *
     * <p>The match is trimmed at a line break so that a label on one line cannot
     * swallow the rest of the document.</p>
     *
     * @param pattern the label pattern to apply
     * @param text    the document text
     * @return the captured value, or empty when the pattern does not match
     */
    private java.util.Optional<String> firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }
        String value = matcher.group(1);
        if (value == null) {
            return java.util.Optional.empty();
        }
        int lineEnd = value.indexOf('\n');
        if (lineEnd >= 0) {
            value = value.substring(0, lineEnd);
        }
        value = value.trim();
        return value.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
