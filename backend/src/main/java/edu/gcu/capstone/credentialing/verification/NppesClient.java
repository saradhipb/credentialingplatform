package edu.gcu.capstone.credentialing.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import edu.gcu.capstone.credentialing.domain.NpiType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * NppesClient.java
 *
 * <p>Client for the CMS NPPES NPI Registry public read API. The registry is the
 * authoritative source for whether an identifier exists, whom it belongs to and
 * whether it is still active, so this class is the platform's primary evidence
 * gatherer for provider identity.</p>
 *
 * <p>Three behaviours deserve note:</p>
 * <ol>
 *   <li><b>Bounded retries.</b> A transient network fault is retried up to
 *       {@code app.nppes.max-retries} times with a short pause, because the most
 *       common failure at this integration point is a momentary timeout rather
 *       than a real outage.</li>
 *   <li><b>Offline fallback.</b> If the live call still fails, a bundled
 *       snapshot of registry records is consulted. The result is marked
 *       {@code OFFLINE_FALLBACK} on the verification record so that no reviewer
 *       can mistake cached data for a live confirmation.</li>
 *   <li><b>No credentials.</b> The NPPES read API is public and requires no key,
 *       so no secret is held in this class or in configuration.</li>
 * </ol>
 */
@Component
public class NppesClient {

    private static final Logger log = LoggerFactory.getLogger(NppesClient.class);

    private final ApplicationProperties properties;
    private final RestTemplate restTemplate;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Registry records bundled with the build, keyed by NPI. */
    private final Map<String, NppesRecord> offlineSnapshot = new HashMap<>();

    /** Set to false after a live call fails, and shown on the API health panel. */
    private volatile boolean liveRegistryReachable = true;

    private volatile String lastLiveError;

    public NppesClient(ApplicationProperties properties,
                       RestTemplate nppesRestTemplate,
                       ResourceLoader resourceLoader) {
        this.properties = properties;
        this.restTemplate = nppesRestTemplate;
        this.resourceLoader = resourceLoader;
    }

    /** Loads the offline snapshot once at start-up so lookups never touch disk. */
    @PostConstruct
    void loadOfflineSnapshot() {
        String location = properties.getNppes().getOfflineDataset();
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                log.warn("NPPES offline snapshot not found at {}", location);
                return;
            }
            try (InputStream in = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                JsonNode records = root.path("records");
                for (JsonNode node : records) {
                    NppesRecord record = toRecord(node);
                    if (record != null) {
                        offlineSnapshot.put(record.npi(), record);
                    }
                }
            }
            log.info("Loaded {} NPPES records into the offline fallback snapshot", offlineSnapshot.size());
        } catch (Exception ex) {
            log.error("Unable to load NPPES offline snapshot from {}: {}", location, ex.toString());
        }
    }

    /**
     * Looks up an identifier in the live registry.
     *
     * @param npi a ten-digit identifier that has already passed the checksum test
     * @return the registry record, or empty when the registry answered but held
     *         no record for that identifier
     * @throws RegistryUnavailableException when the registry could not be reached
     */
    public Optional<NppesRecord> lookupLive(String npi) {
        if (!properties.getNppes().isEnabled()) {
            throw new RegistryUnavailableException("Live NPPES lookups are disabled by configuration");
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(properties.getNppes().getBaseUrl())
                .queryParam("version", properties.getNppes().getVersion())
                .queryParam("number", npi)
                .build(true)
                .toUri();

        Exception lastFailure = null;
        int attempts = properties.getNppes().getMaxRetries() + 1;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String body = restTemplate.getForObject(uri, String.class);
                liveRegistryReachable = true;
                lastLiveError = null;

                JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
                if (root.path("result_count").asInt(0) < 1 || !root.path("results").isArray()
                        || root.path("results").isEmpty()) {
                    return Optional.empty(); // Registry answered: no such record.
                }
                return Optional.ofNullable(toRecord(root.path("results").get(0)));

            } catch (Exception ex) {
                lastFailure = ex;
                log.warn("NPPES lookup attempt {} of {} for NPI {} failed: {}",
                        attempt, attempts, npi, ex.toString());
                if (attempt < attempts) {
                    pauseBeforeRetry(attempt);
                }
            }
        }

        liveRegistryReachable = false;
        lastLiveError = lastFailure == null ? "unknown error" : lastFailure.toString();
        throw new RegistryUnavailableException(
                "NPPES registry unreachable after " + attempts + " attempts", lastFailure);
    }

    /**
     * Looks up an identifier in the bundled offline snapshot.
     *
     * @param npi ten-digit identifier
     * @return the cached record, or empty when the snapshot holds none
     */
    public Optional<NppesRecord> lookupOffline(String npi) {
        return Optional.ofNullable(offlineSnapshot.get(npi));
    }

    /** @return true when the most recent live call succeeded */
    public boolean isLiveRegistryReachable() {
        return liveRegistryReachable;
    }

    /** @return a description of the most recent live failure, or null */
    public String getLastLiveError() {
        return lastLiveError;
    }

    /** @return how many records the offline snapshot holds */
    public int offlineRecordCount() {
        return offlineSnapshot.size();
    }

    /**
     * Maps one NPPES result node to the platform's record type. The same mapping
     * serves the live API and the offline snapshot because the snapshot stores
     * records in the registry's own shape.
     *
     * @param node a single element of the registry's {@code results} array
     * @return the mapped record, or null when the node has no identifier
     */
    private NppesRecord toRecord(JsonNode node) {
        String number = node.path("number").asText(null);
        if (number == null || number.isBlank()) {
            return null;
        }

        JsonNode basic = node.path("basic");
        String enumerationType = node.path("enumeration_type").asText("");
        NpiType type = switch (enumerationType) {
            case "NPI-1" -> NpiType.INDIVIDUAL;
            case "NPI-2" -> NpiType.ORGANIZATION;
            default -> NpiType.UNKNOWN;
        };

        // The practice location is the address a credentialing check compares
        // against; the mailing address is ignored on purpose.
        JsonNode location = null;
        for (JsonNode address : node.path("addresses")) {
            if ("LOCATION".equalsIgnoreCase(address.path("address_purpose").asText(""))) {
                location = address;
                break;
            }
        }
        if (location == null && node.path("addresses").isArray() && !node.path("addresses").isEmpty()) {
            location = node.path("addresses").get(0);
        }

        JsonNode primaryTaxonomy = null;
        for (JsonNode taxonomy : node.path("taxonomies")) {
            if (taxonomy.path("primary").asBoolean(false)) {
                primaryTaxonomy = taxonomy;
                break;
            }
        }

        return new NppesRecord(
                number,
                type,
                textOrNull(basic, "first_name"),
                textOrNull(basic, "last_name"),
                textOrNull(basic, "organization_name"),
                basic.path("status").asText("A"),
                textOrNull(basic, "enumeration_date"),
                location == null ? null : textOrNull(location, "address_1"),
                location == null ? null : textOrNull(location, "city"),
                location == null ? null : textOrNull(location, "state"),
                location == null ? null : textOrNull(location, "postal_code"),
                primaryTaxonomy == null ? null : textOrNull(primaryTaxonomy, "desc"),
                primaryTaxonomy == null ? null : textOrNull(primaryTaxonomy, "license"),
                node.toString());
    }

    /** Reads a text field, returning null rather than an empty string. */
    private String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    /** Waits between retries, lengthening the pause on each attempt. */
    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Raised when the registry itself could not be consulted, as distinct from
     * the registry reporting that no record exists.
     */
    public static class RegistryUnavailableException extends RuntimeException {
        public RegistryUnavailableException(String message) {
            super(message);
        }

        public RegistryUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
