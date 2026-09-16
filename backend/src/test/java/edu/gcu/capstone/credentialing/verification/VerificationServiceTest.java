package edu.gcu.capstone.credentialing.verification;

import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import edu.gcu.capstone.credentialing.domain.*;
import edu.gcu.capstone.credentialing.extraction.DataCleansingService;
import edu.gcu.capstone.credentialing.extraction.ExtractedProviderData;
import edu.gcu.capstone.credentialing.extraction.ProviderDataExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VerificationServiceTest.java
 *
 * <p>Module tests for the verification layer, wired from the real collaborators
 * against the bundled datasets. Live registry lookups are switched off, so every
 * test also exercises the offline fallback path and confirms that results
 * obtained that way are labelled as such.</p>
 *
 * <p>These tests cover the scenarios represented by the bundled sample
 * documents: a clean provider, an excluded provider, a provider whose document
 * disagrees with the registry, a malformed identifier, and a submission with no
 * identifier at all.</p>
 */
class VerificationServiceTest {

    private VerificationService verificationService;
    private ProviderDataExtractor extractor;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getNppes().setEnabled(false);
        properties.getNppes().setOfflineDataset("classpath:data/nppes-offline-snapshot.json");
        properties.getOig().setLeieFile("classpath:data/oig-leie-sample.csv");

        DataCleansingService cleansing = new DataCleansingService();
        DefaultResourceLoader loader = new DefaultResourceLoader();

        NppesClient nppesClient = new NppesClient(properties, new RestTemplate(), loader);
        nppesClient.loadOfflineSnapshot();

        OigExclusionService exclusionService = new OigExclusionService(properties, loader, cleansing);
        exclusionService.loadExclusionList();

        verificationService = new VerificationService(nppesClient, exclusionService, cleansing);
        extractor = new ProviderDataExtractor(cleansing);
    }

    @Test
    @DisplayName("A matching provider verifies with no discrepancies and is not excluded")
    void verifiesCleanProvider() {
        ExtractedProviderData data = extractor.extract("""
                Provider Name: Harper, Dana R., M.D.
                Entity Type: 1
                NPI Number: 1746805930
                License Number: A12345
                Practice Address: 4410 N Central Ave Ste 210
                Phoenix, AZ 85012
                """);

        VerificationRecord record = verificationService.verify(data, new Provider());

        assertThat(record.getNpiStatus()).isEqualTo(NpiVerificationStatus.VALID);
        assertThat(record.getExclusionStatus()).isEqualTo(ExclusionStatus.NOT_EXCLUDED);
        assertThat(record.getDiscrepancyCount()).isZero();
        assertThat(record.getSource()).isEqualTo(VerificationSource.OFFLINE_FALLBACK);
        assertThat(record.getRegistryName()).contains("HARPER");
    }

    @Test
    @DisplayName("An excluded provider is identified by identifier match")
    void detectsExcludedProvider() {
        ExtractedProviderData data = extractor.extract("""
                Provider Name: Whitfield, Reginald T., M.D.
                Entity Type: 1
                NPI: 1458293762
                Practice Address: 2810 E Speedway Blvd
                Tucson, AZ 85716
                """);

        VerificationRecord record = verificationService.verify(data, new Provider());

        assertThat(record.getExclusionStatus()).isEqualTo(ExclusionStatus.EXCLUDED);
        assertThat(record.getExclusionType()).isEqualTo("1128b4");
        assertThat(record.getExclusionMatchedName()).containsIgnoringCase("WHITFIELD");
    }

    @Test
    @DisplayName("An address that disagrees with the registry is reported as a discrepancy")
    void detectsAddressDiscrepancy() {
        ExtractedProviderData data = extractor.extract("""
                Provider Name: Okafor, Chidi, M.D.
                Entity Type: 1
                National Provider Identifier: 1283947566
                License Number: B77120
                Practice Address: 4455 E Camelback Rd Suite 300
                Scottsdale, AZ 85018
                """);

        VerificationRecord record = verificationService.verify(data, new Provider());

        assertThat(record.getNpiStatus()).isEqualTo(NpiVerificationStatus.VALID);
        assertThat(record.getDiscrepancyCount()).isGreaterThan(0);
        assertThat(record.getDiscrepanciesJson()).contains("City");
    }

    @Test
    @DisplayName("A deactivated registry record is reported as deactivated, not valid")
    void detectsDeactivatedIdentifier() {
        ExtractedProviderData data = extractor.extract("""
                Provider Name: Alvarado, Beatriz, N.P.
                NPI: 1192837460
                Practice Address: 1875 W Chandler Blvd
                Chandler, AZ 85224
                """);

        VerificationRecord record = verificationService.verify(data, new Provider());

        assertThat(record.getNpiStatus()).isEqualTo(NpiVerificationStatus.DEACTIVATED);
    }

    @Test
    @DisplayName("A malformed identifier is rejected without any registry lookup")
    void rejectsMalformedIdentifier() {
        ExtractedProviderData data = extractor.extract("""
                Provider Name: Sandoval, Marisol, D.O.
                NPI Number: 1234567890
                """);

        VerificationRecord record = verificationService.verify(data, new Provider());

        assertThat(record.getNpiStatus()).isEqualTo(NpiVerificationStatus.INVALID_FORMAT);
        assertThat(record.getSource()).isEqualTo(VerificationSource.NONE);
        assertThat(record.getProcessingNote()).contains("check-digit");
    }

    @Test
    @DisplayName("A submission with no identifier is reported as missing, not as invalid")
    void reportsMissingIdentifier() {
        ExtractedProviderData data = extractor.extract("""
                Provider Name: Ferreira, Joaquim
                License Number: K90233
                """);

        VerificationRecord record = verificationService.verify(data, new Provider());

        assertThat(record.getNpiStatus()).isEqualTo(NpiVerificationStatus.MISSING);
        assertThat(record.getMissingFieldCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("A valid identifier absent from the registry is reported as not found")
    void reportsUnknownIdentifier() {
        ExtractedProviderData data = extractor.extract("""
                Provider Name: Calloway, Marcus D., M.D.
                NPI Number: 1234567893
                Practice Address: 118 W Adams St
                Phoenix, AZ 85003
                """);

        VerificationRecord record = verificationService.verify(data, new Provider());

        assertThat(record.getNpiStatus()).isEqualTo(NpiVerificationStatus.UNAVAILABLE);
        assertThat(record.getExclusionStatus()).isEqualTo(ExclusionStatus.POSSIBLE_MATCH);
    }
}
