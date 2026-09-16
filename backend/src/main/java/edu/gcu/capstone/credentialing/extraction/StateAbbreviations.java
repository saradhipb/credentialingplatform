package edu.gcu.capstone.credentialing.extraction;

import java.util.Locale;
import java.util.Map;

/**
 * StateAbbreviations.java
 *
 * <p>Lookup table mapping full United States state and territory names to their
 * two-letter USPS codes. Documents print the state either way, so reducing both
 * forms to the code before comparison removes a common source of false
 * mismatches.</p>
 */
public final class StateAbbreviations {

    private static final Map<String, String> NAME_TO_CODE = Map.ofEntries(
            Map.entry("alabama", "AL"), Map.entry("alaska", "AK"), Map.entry("arizona", "AZ"),
            Map.entry("arkansas", "AR"), Map.entry("california", "CA"), Map.entry("colorado", "CO"),
            Map.entry("connecticut", "CT"), Map.entry("delaware", "DE"), Map.entry("florida", "FL"),
            Map.entry("georgia", "GA"), Map.entry("hawaii", "HI"), Map.entry("idaho", "ID"),
            Map.entry("illinois", "IL"), Map.entry("indiana", "IN"), Map.entry("iowa", "IA"),
            Map.entry("kansas", "KS"), Map.entry("kentucky", "KY"), Map.entry("louisiana", "LA"),
            Map.entry("maine", "ME"), Map.entry("maryland", "MD"), Map.entry("massachusetts", "MA"),
            Map.entry("michigan", "MI"), Map.entry("minnesota", "MN"), Map.entry("mississippi", "MS"),
            Map.entry("missouri", "MO"), Map.entry("montana", "MT"), Map.entry("nebraska", "NE"),
            Map.entry("nevada", "NV"), Map.entry("new hampshire", "NH"), Map.entry("new jersey", "NJ"),
            Map.entry("new mexico", "NM"), Map.entry("new york", "NY"), Map.entry("north carolina", "NC"),
            Map.entry("north dakota", "ND"), Map.entry("ohio", "OH"), Map.entry("oklahoma", "OK"),
            Map.entry("oregon", "OR"), Map.entry("pennsylvania", "PA"), Map.entry("rhode island", "RI"),
            Map.entry("south carolina", "SC"), Map.entry("south dakota", "SD"), Map.entry("tennessee", "TN"),
            Map.entry("texas", "TX"), Map.entry("utah", "UT"), Map.entry("vermont", "VT"),
            Map.entry("virginia", "VA"), Map.entry("washington", "WA"), Map.entry("west virginia", "WV"),
            Map.entry("wisconsin", "WI"), Map.entry("wyoming", "WY"),
            Map.entry("district of columbia", "DC"), Map.entry("puerto rico", "PR"));

    private StateAbbreviations() {
    }

    /**
     * Converts a full state name to its USPS code.
     *
     * @param name full state name in any letter case
     * @return the two-letter code, or null when the name is not recognised
     */
    public static String toCode(String name) {
        if (name == null) {
            return null;
        }
        return NAME_TO_CODE.get(name.trim().toLowerCase(Locale.ROOT));
    }
}
