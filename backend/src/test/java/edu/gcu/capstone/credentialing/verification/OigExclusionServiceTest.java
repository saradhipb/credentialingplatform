package edu.gcu.capstone.credentialing.verification;

import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import edu.gcu.capstone.credentialing.domain.ExclusionStatus;
import edu.gcu.capstone.credentialing.extraction.DataCleansingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OigExclusionServiceTest.java
 *
 * <p>Module tests for exclusion screening against the bundled sample list. Both
 * screening passes are exercised, and the distinction between them is asserted:
 * an identifier match is reported as EXCLUDED, while a name-only match is
 * reported as POSSIBLE_MATCH and never as a confirmed exclusion.</p>
 */
class OigExclusionServiceTest {

    private OigExclusionService service;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getOig().setLeieFile("classpath:data/oig-leie-sample.csv");
        properties.getOig().setNameMatchThreshold(0.92);

        service = new OigExclusionService(properties, new DefaultResourceLoader(),
                new DataCleansingService());
        service.loadExclusionList();
    }

    @Test
    @DisplayName("The bundled exclusion list loads at start-up")
    void loadsTheList() {
        assertThat(service.isListLoaded()).isTrue();
        assertThat(service.recordCount()).isGreaterThan(10);
    }

    @Test
    @DisplayName("An identifier match is reported as a confirmed exclusion")
    void identifierMatchIsConfirmed() {
        var result = service.screen("1458293762", "Whitfield Reginald");

        assertThat(result.status()).isEqualTo(ExclusionStatus.EXCLUDED);
        assertThat(result.match()).isNotNull();
        assertThat(result.match().displayName()).containsIgnoringCase("WHITFIELD");
    }

    @Test
    @DisplayName("A name-only match is reported for confirmation, not as an exclusion")
    void nameMatchIsReportedForReview() {
        var result = service.screen("1234567893", "Calloway Marcus D");

        assertThat(result.status()).isEqualTo(ExclusionStatus.POSSIBLE_MATCH);
        assertThat(result.matchScore()).isGreaterThanOrEqualTo(0.92);
    }

    @Test
    @DisplayName("A provider who is not on the list is cleared")
    void unrelatedProviderIsCleared() {
        var result = service.screen("1746805930", "Harper Dana R");

        assertThat(result.status()).isEqualTo(ExclusionStatus.NOT_EXCLUDED);
        assertThat(result.match()).isNull();
    }

    @Test
    @DisplayName("A reinstated exclusion no longer matches")
    void reinstatedExclusionDoesNotMatch() {
        var result = service.screen(null, "Abernathy Gordon L");

        assertThat(result.status()).isEqualTo(ExclusionStatus.NOT_EXCLUDED);
    }

    @Test
    @DisplayName("Screening with no identifier and no name is handled safely")
    void handlesEmptyInput() {
        var result = service.screen(null, null);

        assertThat(result.status()).isEqualTo(ExclusionStatus.NOT_EXCLUDED);
    }
}
