package edu.gcu.capstone.credentialing.verification;

import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import edu.gcu.capstone.credentialing.domain.ExclusionStatus;
import edu.gcu.capstone.credentialing.extraction.DataCleansingService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OigExclusionService.java
 *
 * <p>Screens providers against the HHS OIG List of Excluded Individuals/Entities.
 * An exclusion is the single most consequential finding the platform can
 * produce, because an excluded provider may not be paid by any federal health
 * care programme, so this check is run for every provider regardless of what the
 * NPI lookup returned.</p>
 *
 * <p>Screening proceeds in two passes:</p>
 * <ol>
 *   <li><b>Identifier match.</b> When the exclusion record publishes an NPI and
 *       it equals the provider's NPI, the match is definitive and reported as
 *       {@link ExclusionStatus#EXCLUDED}.</li>
 *   <li><b>Name match.</b> Many LEIE rows predate NPI publication and carry no
 *       identifier, so names are compared with Jaro-Winkler similarity. A score
 *       at or above the configured threshold is reported as
 *       {@link ExclusionStatus#POSSIBLE_MATCH} and routed to a human, never
 *       treated as a confirmed exclusion. Deciding a person's exclusion status
 *       on a fuzzy name alone would be indefensible.</li>
 * </ol>
 *
 * <p>The list is loaded once at start-up and held in memory: the published file
 * is a static monthly extract, and an in-memory index keeps screening well
 * inside the two-second verification budget set in Milestone 2.</p>
 */
@Service
public class OigExclusionService {

    private static final Logger log = LoggerFactory.getLogger(OigExclusionService.class);

    private final ApplicationProperties properties;
    private final ResourceLoader resourceLoader;
    private final DataCleansingService cleansingService;

    /** Exclusion rows indexed by NPI for constant-time identifier screening. */
    private final Map<String, LeieRecord> byNpi = new HashMap<>();

    /** All rows, scanned when a name comparison is required. */
    private final List<LeieRecord> allRecords = new ArrayList<>();

    /** Comparison keys precomputed once so screening does not re-normalise per row. */
    private final List<String> nameKeys = new ArrayList<>();

    private volatile boolean listLoaded;

    public OigExclusionService(ApplicationProperties properties,
                               ResourceLoader resourceLoader,
                               DataCleansingService cleansingService) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.cleansingService = cleansingService;
    }

    /** Parses the LEIE extract into memory at application start-up. */
    @PostConstruct
    void loadExclusionList() {
        String location = properties.getOig().getLeieFile();
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                log.error("OIG exclusion list not found at {}. Exclusion screening will report UNAVAILABLE.",
                        location);
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String header = reader.readLine(); // Column headings, not data.
                if (header == null) {
                    log.error("OIG exclusion list at {} was empty", location);
                    return;
                }

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    LeieRecord record = parseRow(line);
                    if (record == null) {
                        continue;
                    }
                    allRecords.add(record);
                    nameKeys.add(cleansingService.comparisonKey(record.displayName()));
                    if (record.hasUsableNpi()) {
                        byNpi.put(record.npi(), record);
                    }
                }
            }

            listLoaded = true;
            log.info("Loaded {} OIG exclusion records ({} with a published NPI)",
                    allRecords.size(), byNpi.size());

        } catch (Exception ex) {
            log.error("Unable to load the OIG exclusion list from {}: {}", location, ex.toString());
        }
    }

    /**
     * Screens one provider against the exclusion list.
     *
     * @param npi              the provider's identifier, may be null
     * @param providerFullName the provider's name as extracted, may be null
     * @return the screening outcome, never null
     */
    public ExclusionScreeningResult screen(String npi, String providerFullName) {
        if (!listLoaded) {
            return new ExclusionScreeningResult(ExclusionStatus.UNAVAILABLE, null, 0.0,
                    "The OIG exclusion list could not be loaded.");
        }

        // Pass 1: definitive identifier match.
        if (npi != null && !npi.isBlank()) {
            LeieRecord byIdentifier = byNpi.get(npi);
            if (byIdentifier != null && byIdentifier.isActive()) {
                return new ExclusionScreeningResult(ExclusionStatus.EXCLUDED, byIdentifier, 1.0,
                        "NPI matches an active OIG exclusion record.");
            }
        }

        // Pass 2: similarity match on the name, reported for review only.
        String candidateKey = cleansingService.comparisonKey(providerFullName);
        if (candidateKey.isBlank()) {
            return new ExclusionScreeningResult(ExclusionStatus.NOT_EXCLUDED, null, 0.0,
                    "No identifier or name match found on the exclusion list.");
        }

        double bestScore = 0.0;
        LeieRecord bestMatch = null;
        for (int i = 0; i < allRecords.size(); i++) {
            LeieRecord record = allRecords.get(i);
            if (!record.isActive()) {
                continue;
            }
            double score = StringSimilarity.jaroWinkler(candidateKey, nameKeys.get(i));
            if (score > bestScore) {
                bestScore = score;
                bestMatch = record;
            }
        }

        if (bestMatch != null && bestScore >= properties.getOig().getNameMatchThreshold()) {
            return new ExclusionScreeningResult(ExclusionStatus.POSSIBLE_MATCH, bestMatch, bestScore,
                    String.format("Name is %.0f%% similar to an excluded party; human confirmation required.",
                            bestScore * 100));
        }

        return new ExclusionScreeningResult(ExclusionStatus.NOT_EXCLUDED, null, bestScore,
                "No identifier or name match found on the exclusion list.");
    }

    /** @return the number of exclusion records currently loaded */
    public int recordCount() {
        return allRecords.size();
    }

    /** @return true when the exclusion list loaded successfully at start-up */
    public boolean isListLoaded() {
        return listLoaded;
    }

    /**
     * Parses one LEIE row. The published extract is comma separated with quoted
     * fields, so a small state machine is used rather than a naive split, which
     * would break on entity names that contain commas.
     *
     * @param line one CSV data row
     * @return the parsed record, or null when the row has too few columns
     */
    private LeieRecord parseRow(String line) {
        List<String> fields = splitCsv(line);
        if (fields.size() < 15) {
            return null;
        }
        return new LeieRecord(
                trimToNull(fields.get(0)),   // LASTNAME
                trimToNull(fields.get(1)),   // FIRSTNAME
                trimToNull(fields.get(2)),   // MIDNAME
                trimToNull(fields.get(3)),   // BUSNAME
                trimToNull(fields.get(4)),   // GENERAL
                trimToNull(fields.get(5)),   // SPECIALTY
                trimToNull(fields.get(7)),   // NPI
                trimToNull(fields.get(13)),  // EXCLTYPE
                trimToNull(fields.get(14)),  // EXCLDATE
                fields.size() > 15 ? trimToNull(fields.get(15)) : null, // REINDATE
                trimToNull(fields.get(11))); // STATE
    }

    /**
     * Splits a CSV line, honouring double-quoted fields and escaped quotes.
     *
     * @param line the raw line
     * @return the field values in column order
     */
    private List<String> splitCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"'); // Escaped quote inside a quoted field.
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Outcome of one exclusion screen.
     *
     * @param status     the screening result
     * @param match      the exclusion row that matched, or null
     * @param matchScore similarity of the best name match, 0.0-1.0
     * @param note       plain-language explanation shown to the reviewer
     */
    public record ExclusionScreeningResult(ExclusionStatus status, LeieRecord match,
                                           double matchScore, String note) {
    }
}
