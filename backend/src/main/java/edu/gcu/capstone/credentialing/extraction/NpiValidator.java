package edu.gcu.capstone.credentialing.extraction;

/**
 * NpiValidator.java
 *
 * <p>Validates a National Provider Identifier using the check-digit algorithm
 * published by CMS. An NPI is ten digits: nine identifier digits followed by a
 * check digit computed with the Luhn formula over the identifier digits
 * prefixed by the constant 80840, which is the health-industry prefix assigned
 * under ISO/IEC 7812.</p>
 *
 * <p>Validating locally before calling the registry matters for two reasons. It
 * removes a network round trip for numbers that cannot possibly be real, and it
 * separates two findings that must never be confused: a number that fails the
 * checksum is a defect in the document, whereas a well-formed number the
 * registry does not recognise is a finding about the provider.</p>
 *
 * <p>Worked example for NPI 1234567893:</p>
 * <pre>
 *   identifier digits  123456789
 *   with prefix        80840123456789
 *   Luhn sum           67
 *   check digit        (10 - 67 mod 10) mod 10 = 3   matches the tenth digit
 * </pre>
 */
public final class NpiValidator {

    /** Health-industry prefix prepended before the Luhn computation. */
    private static final String ISO_PREFIX = "80840";

    /** Utility class; not intended to be instantiated. */
    private NpiValidator() {
    }

    /**
     * Tests whether a string is a structurally valid NPI.
     *
     * @param npi candidate identifier; surrounding whitespace and embedded
     *            hyphens or spaces are tolerated
     * @return true when the value is ten digits and the check digit is correct
     */
    public static boolean isValid(String npi) {
        String digits = normalise(npi);
        if (digits.length() != 10) {
            return false;
        }
        int expected = computeCheckDigit(digits.substring(0, 9));
        int actual = digits.charAt(9) - '0';
        return expected == actual;
    }

    /**
     * Computes the check digit for the nine identifier digits of an NPI.
     *
     * @param identifierDigits exactly nine digits
     * @return the check digit in the range 0-9
     * @throws IllegalArgumentException if the input is not nine digits
     */
    public static int computeCheckDigit(String identifierDigits) {
        if (identifierDigits == null || !identifierDigits.matches("\\d{9}")) {
            throw new IllegalArgumentException("NPI identifier portion must be exactly nine digits");
        }
        String payload = ISO_PREFIX + identifierDigits;

        int sum = 0;
        boolean doubleThisDigit = true; // The rightmost payload digit is doubled.
        for (int i = payload.length() - 1; i >= 0; i--) {
            int digit = payload.charAt(i) - '0';
            if (doubleThisDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9; // Equivalent to summing the two decimal digits.
                }
            }
            sum += digit;
            doubleThisDigit = !doubleThisDigit;
        }
        return (10 - (sum % 10)) % 10;
    }

    /**
     * Strips formatting characters so "1234-567-893" and " 1234567893 " are both
     * recognised. Optical recognition of printed identifiers commonly confuses
     * the letter O with zero and the letter l with one, so those two
     * substitutions are applied before validation.
     *
     * @param npi raw value read from a document, may be null
     * @return digits only, never null
     */
    public static String normalise(String npi) {
        if (npi == null) {
            return "";
        }
        return npi.trim()
                .replace('O', '0').replace('o', '0')
                .replace('l', '1').replace('I', '1')
                .replaceAll("[^0-9]", "");
    }
}
