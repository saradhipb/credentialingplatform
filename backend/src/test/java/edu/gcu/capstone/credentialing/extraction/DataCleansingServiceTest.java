package edu.gcu.capstone.credentialing.extraction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DataCleansingServiceTest.java
 *
 * <p>Module tests for the normalisation rules. The comparison-key tests are the
 * important ones: they assert that the three formatting differences most likely
 * to produce a false discrepancy - letter case, name order and professional
 * suffixes - reduce to the same key.</p>
 */
class DataCleansingServiceTest {

    private DataCleansingService service;

    @BeforeEach
    void setUp() {
        service = new DataCleansingService();
    }

    @Test
    @DisplayName("Runs of whitespace collapse to a single space")
    void collapsesWhitespace() {
        assertThat(service.normaliseWhitespace("  4410   N  Central   Ave  "))
                .isEqualTo("4410 N Central Ave");
    }

    @Test
    @DisplayName("Entity suffixes and credentials keep their capitals")
    void preservesAcronyms() {
        assertThat(service.normaliseName("SAGUARO VALLEY FAMILY CLINIC LLC"))
                .isEqualTo("Saguaro Valley Family Clinic LLC");
        assertThat(service.normaliseName("copper state imaging pc"))
                .isEqualTo("Copper State Imaging PC");
    }

    @Test
    @DisplayName("Names are title cased, including after hyphens and apostrophes")
    void titleCasesNames() {
        assertThat(service.normaliseName("HARPER")).isEqualTo("Harper");
        assertThat(service.normaliseName("osei-bonsu")).isEqualTo("Osei-Bonsu");
        assertThat(service.normaliseName("o'neill")).isEqualTo("O'Neill");
    }

    @Test
    @DisplayName("Case, order and professional suffixes do not change the comparison key")
    void comparisonKeyIgnoresFormatting() {
        String fromDocument = service.comparisonKey("Harper, Dana R., M.D.");
        String fromRegistry = service.comparisonKey("DANA R HARPER MD");
        assertThat(fromDocument).isEqualTo(fromRegistry);
    }

    @Test
    @DisplayName("Genuinely different names produce different comparison keys")
    void comparisonKeyStillSeparatesDifferentNames() {
        assertThat(service.comparisonKey("Harper, Dana"))
                .isNotEqualTo(service.comparisonKey("Okafor, Chidi"));
    }

    @Test
    @DisplayName("Full state names reduce to their USPS code")
    void normalisesStateNames() {
        assertThat(service.normaliseState("Arizona")).isEqualTo("AZ");
        assertThat(service.normaliseState("az")).isEqualTo("AZ");
        assertThat(service.normaliseState("New Mexico")).isEqualTo("NM");
    }

    @Test
    @DisplayName("ZIP+4 codes reduce to the five-digit form")
    void normalisesPostalCodes() {
        assertThat(service.normalisePostalCode("85012-1847")).isEqualTo("85012");
        assertThat(service.normalisePostalCode("850121847")).isEqualTo("85012");
        assertThat(service.normalisePostalCode("85012")).isEqualTo("85012");
    }

    @Test
    @DisplayName("License numbers lose separators and letter case")
    void normalisesLicenseNumbers() {
        assertThat(service.normaliseLicenseNumber("a-123 45")).isEqualTo("A12345");
    }

    @Test
    @DisplayName("Null input is tolerated everywhere")
    void toleratesNulls() {
        assertThat(service.normaliseWhitespace(null)).isNull();
        assertThat(service.normaliseName(null)).isNull();
        assertThat(service.normaliseState(null)).isNull();
        assertThat(service.normalisePostalCode(null)).isNull();
        assertThat(service.comparisonKey(null)).isEmpty();
    }
}
