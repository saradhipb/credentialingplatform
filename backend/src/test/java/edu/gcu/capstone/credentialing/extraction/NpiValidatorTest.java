package edu.gcu.capstone.credentialing.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NpiValidatorTest.java
 *
 * <p>Module tests for the CMS check-digit implementation. The worked example
 * documented on {@code NpiValidator} is asserted directly, and a number that
 * differs from a valid identifier only in its check digit is asserted to be
 * rejected, which is the case a naive length-only check would let through.</p>
 */
class NpiValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"1234567893", "1746805930", "2103948578", "1458293762", "1637482955"})
    @DisplayName("Identifiers with a correct check digit are accepted")
    void acceptsValidIdentifiers(String npi) {
        assertThat(NpiValidator.isValid(npi)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "1746805931", "0000000000", "9999999999"})
    @DisplayName("Identifiers with an incorrect check digit are rejected")
    void rejectsBadCheckDigits(String npi) {
        assertThat(NpiValidator.isValid(npi)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "12345678931", "", "   ", "ABCDEFGHIJ"})
    @DisplayName("Values that are not ten digits are rejected")
    void rejectsWrongLength(String npi) {
        assertThat(NpiValidator.isValid(npi)).isFalse();
    }

    @Test
    @DisplayName("A null identifier is rejected rather than causing an error")
    void rejectsNull() {
        assertThat(NpiValidator.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("The documented worked example produces check digit 3")
    void computesDocumentedCheckDigit() {
        assertThat(NpiValidator.computeCheckDigit("123456789")).isEqualTo(3);
    }

    @Test
    @DisplayName("Formatting characters and common recognition confusions are normalised")
    void normalisesFormattingAndOcrConfusions() {
        assertThat(NpiValidator.normalise(" 1234-567-893 ")).isEqualTo("1234567893");
        assertThat(NpiValidator.normalise("l7468O593O")).isEqualTo("1746805930");
        assertThat(NpiValidator.isValid("l7468O593O")).isTrue();
    }

    @Test
    @DisplayName("Computing a check digit requires exactly nine digits")
    void rejectsMalformedIdentifierPortion() {
        assertThatThrownBy(() -> NpiValidator.computeCheckDigit("12345"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
