package edu.gcu.capstone.credentialing.verification;

import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import edu.gcu.capstone.credentialing.domain.NpiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NppesClientOfflineTest.java
 *
 * <p>Module tests for the registry client's offline behaviour. No network call
 * is made: the tests confirm that the bundled snapshot loads, that records map
 * into the platform's own type correctly, and that disabling live lookups raises
 * a registry-unavailable condition rather than quietly returning "not found",
 * which is the distinction the whole fallback design depends on.</p>
 */
class NppesClientOfflineTest {

    private NppesClient client;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getNppes().setEnabled(false);
        properties.getNppes().setOfflineDataset("classpath:data/nppes-offline-snapshot.json");

        client = new NppesClient(properties, new RestTemplate(), new DefaultResourceLoader());
        client.loadOfflineSnapshot();
    }

    @Test
    @DisplayName("The bundled snapshot loads at start-up")
    void loadsSnapshot() {
        assertThat(client.offlineRecordCount()).isGreaterThan(10);
    }

    @Test
    @DisplayName("An individual record maps to a Type 1 provider with its practice address")
    void mapsIndividualRecord() {
        var record = client.lookupOffline("1746805930").orElseThrow();

        assertThat(record.npiType()).isEqualTo(NpiType.INDIVIDUAL);
        assertThat(record.lastName()).isEqualTo("HARPER");
        assertThat(record.isActive()).isTrue();
        assertThat(record.city()).isEqualTo("PHOENIX");
        assertThat(record.taxonomyLicense()).isEqualTo("A12345");
    }

    @Test
    @DisplayName("An organization record maps to a Type 2 provider with its business name")
    void mapsOrganizationRecord() {
        var record = client.lookupOffline("2103948578").orElseThrow();

        assertThat(record.npiType()).isEqualTo(NpiType.ORGANIZATION);
        assertThat(record.displayName()).isEqualTo("SAGUARO VALLEY FAMILY CLINIC LLC");
    }

    @Test
    @DisplayName("A deactivated registry record is reported as inactive")
    void detectsDeactivatedRecord() {
        var record = client.lookupOffline("1192837460").orElseThrow();

        assertThat(record.isActive()).isFalse();
    }

    @Test
    @DisplayName("An identifier absent from the snapshot returns no record")
    void returnsEmptyForUnknownIdentifier() {
        assertThat(client.lookupOffline("1234567893")).isEmpty();
    }

    @Test
    @DisplayName("Disabling live lookups raises unavailability, not a not-found result")
    void disabledLiveLookupRaisesUnavailable() {
        assertThatThrownBy(() -> client.lookupLive("1746805930"))
                .isInstanceOf(NppesClient.RegistryUnavailableException.class);
    }
}
