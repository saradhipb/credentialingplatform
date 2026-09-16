package edu.gcu.capstone.credentialing.extraction;

import edu.gcu.capstone.credentialing.domain.NpiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProviderDataExtractorTest.java
 *
 * <p>Module tests for the field parser, exercised against text in the same shape
 * as the bundled sample documents. The most valuable assertions here are the
 * negative ones: that a fax number is not mistaken for an identifier, and that
 * fields which are genuinely absent are reported as missing rather than
 * silently returned as empty.</p>
 */
class ProviderDataExtractorTest {

    private ProviderDataExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ProviderDataExtractor(new DataCleansingService());
    }

    @Test
    @DisplayName("A labelled verification letter yields every expected field")
    void parsesLabelledDocument() {
        String text = """
                ARIZONA MEDICAL BOARD
                PRIMARY SOURCE VERIFICATION LETTER

                Provider Name: Harper, Dana R., M.D.
                Entity Type: 1
                NPI Number: 1746805930
                License Number: A12345
                License State: Arizona
                Specialty: Family Medicine

                Practice Address: 4410 N Central Ave Ste 210
                Phoenix, AZ 85012
                """;

        ExtractedProviderData data = extractor.extract(text);

        assertThat(data.getNpi()).isEqualTo("1746805930");
        assertThat(data.isNpiChecksumValid()).isTrue();
        assertThat(data.getLastName()).isEqualTo("Harper");
        assertThat(data.getFirstName()).isEqualTo("Dana R");
        assertThat(data.getNpiType()).isEqualTo(NpiType.INDIVIDUAL);
        assertThat(data.getLicenseNumber()).isEqualTo("A12345");
        assertThat(data.getLicenseState()).isEqualTo("AZ");
        assertThat(data.getCity()).isEqualTo("Phoenix");
        assertThat(data.getState()).isEqualTo("AZ");
        assertThat(data.getPostalCode()).isEqualTo("85012");
        assertThat(data.getMissingFields()).isEmpty();
    }

    @Test
    @DisplayName("An organization submission is classified as a Type 2 entity")
    void parsesOrganizationDocument() {
        String text = """
                GROUP PRACTICE ENROLMENT PACKET

                Organization Name: Saguaro Valley Family Clinic LLC
                Entity Type: 2
                NPI Number: 2103948578
                License Number: CL-88431
                License State: Arizona

                Business Address: 5902 W Northern Ave
                Glendale, AZ 85301
                """;

        ExtractedProviderData data = extractor.extract(text);

        assertThat(data.getOrganizationName()).isEqualTo("Saguaro Valley Family Clinic LLC");
        assertThat(data.getNpiType()).isEqualTo(NpiType.ORGANIZATION);
        assertThat(data.getNpi()).isEqualTo("2103948578");
        assertThat(data.getCity()).isEqualTo("Glendale");
    }

    @Test
    @DisplayName("An identifier with a bad check digit is kept but marked invalid")
    void keepsInvalidIdentifierAndFlagsIt() {
        ExtractedProviderData data = extractor.extract("""
                Provider Name: Sandoval, Marisol, D.O.
                NPI Number: 1234567890
                """);

        assertThat(data.getNpi()).isEqualTo("1234567890");
        assertThat(data.isNpiChecksumValid()).isFalse();
    }

    @Test
    @DisplayName("Absent fields are reported as missing")
    void reportsMissingFields() {
        ExtractedProviderData data = extractor.extract("""
                Provider Name: Ferreira, Joaquim
                License Number: K90233
                License State: CA
                """);

        assertThat(data.getNpi()).isNull();
        assertThat(data.getMissingFields())
                .contains("npi", "addressLine1", "city", "state", "postalCode");
    }

    @Test
    @DisplayName("A ten-digit fax number is not mistaken for an identifier")
    void ignoresTenDigitNumbersThatAreNotIdentifiers() {
        ExtractedProviderData data = extractor.extract("""
                Provider Name: Lindqvist, Marta
                Telephone: 6025550147
                Fax: 6025550148
                """);

        assertThat(data.getNpi()).isNull();
        assertThat(data.getMissingFields()).contains("npi");
    }

    @Test
    @DisplayName("A name printed without a comma is split with the surname last")
    void splitsUncommaedName() {
        ExtractedProviderData data = extractor.extract("Provider Name: Kenji Nakamura, M.D.");

        assertThat(data.getLastName()).isEqualTo("Nakamura");
        assertThat(data.getFirstName()).isEqualTo("Kenji");
    }

    @Test
    @DisplayName("A license number is not invented from the letters l-i-c inside ordinary words")
    void doesNotInventLicenseFromOrdinaryWords() {
        // "LICENSING" and "APPLICATION" both contain the letters l-i-c. An
        // earlier pattern matched them and captured whatever followed, giving
        // license numbers of "ENSING" and "ATION" which were then compared with
        // the registry and reported as discrepancies. See code review CR-11.
        ExtractedProviderData fromHeading = extractor.extract("""
                STATE LICENSING BOARD
                CREDENTIALING APPLICATION - ATTACHMENT B

                Provider Name: Whitfield, Reginald T., M.D.
                NPI: 1458293762
                """);

        assertThat(fromHeading.getLicenseNumber()).isNull();
        assertThat(fromHeading.getMissingFields()).contains("licenseNumber");
    }

    @Test
    @DisplayName("A genuine license label is still read in all its usual forms")
    void readsEveryUsualLicenseLabel() {
        assertThat(extractor.extract("License Number: A12345").getLicenseNumber()).isEqualTo("A12345");
        assertThat(extractor.extract("Licence No. B77120").getLicenseNumber()).isEqualTo("B77120");
        assertThat(extractor.extract("License: C30845").getLicenseNumber()).isEqualTo("C30845");
        assertThat(extractor.extract("Lic # D55219").getLicenseNumber()).isEqualTo("D55219");
    }

    @Test
    @DisplayName("A license state label is not mistaken for a license number")
    void doesNotReadTheStateAsTheLicenseNumber() {
        ExtractedProviderData data = extractor.extract("""
                Provider Name: Harper, Dana R., M.D.
                License State: Arizona
                """);

        assertThat(data.getLicenseNumber()).isNull();
        assertThat(data.getLicenseState()).isEqualTo("AZ");
    }

    @Test
    @DisplayName("An organization name is not split into a personal first and last name")
    void doesNotSplitAnOrganizationNameIntoAPersonName() {
        // The bare "name" alternative also matches the label "Organization Name:",
        // which previously stored "Saguaro" and "LLC" as a person's names.
        // See code review CR-12.
        ExtractedProviderData data = extractor.extract("""
                Organization Name: Saguaro Valley Family Clinic LLC
                Entity Type: 2
                NPI Number: 2103948578
                """);

        assertThat(data.getOrganizationName()).isEqualTo("Saguaro Valley Family Clinic LLC");
        assertThat(data.getFirstName()).isNull();
        assertThat(data.getLastName()).isNull();
    }

    @Test
    @DisplayName("Empty text produces an unusable result rather than an error")
    void handlesEmptyText() {
        ExtractedProviderData data = extractor.extract("");

        assertThat(data.isUsable()).isFalse();
        assertThat(data.getMissingFields()).isNotEmpty();
    }
}
