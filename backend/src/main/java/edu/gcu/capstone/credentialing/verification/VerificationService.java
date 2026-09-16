package edu.gcu.capstone.credentialing.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.gcu.capstone.credentialing.domain.*;
import edu.gcu.capstone.credentialing.extraction.DataCleansingService;
import edu.gcu.capstone.credentialing.extraction.ExtractedProviderData;
import edu.gcu.capstone.credentialing.extraction.NpiValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * VerificationService.java
 *
 * <p>The verification layer of the architecture. It takes the fields parsed from
 * a document and produces the platform's primary evidence: whether the
 * identifier is valid and active in the CMS registry, whether the provider
 * appears on the OIG exclusion list, and where the document disagrees with the
 * registry.</p>
 *
 * <p>Two principles govern this class. First, a finding about the provider and a
 * finding about the infrastructure are recorded differently: an unreachable
 * registry yields {@link NpiVerificationStatus#UNAVAILABLE}, never
 * {@code NOT_FOUND}. Second, both sides of every comparison are cleansed with
 * the same rules before being compared, so formatting differences cannot present
 * themselves as discrepancies.</p>
 *
 * <p>This class satisfies functional requirement FR-03 (automated verification
 * against NPI and OIG sources).</p>
 */
@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final NppesClient nppesClient;
    private final OigExclusionService exclusionService;
    private final DataCleansingService cleansingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VerificationService(NppesClient nppesClient,
                               OigExclusionService exclusionService,
                               DataCleansingService cleansingService) {
        this.nppesClient = nppesClient;
        this.exclusionService = exclusionService;
        this.cleansingService = cleansingService;
    }

    /**
     * Verifies one provider against the external authoritative sources.
     *
     * @param data     fields parsed and cleansed from the submitted document
     * @param provider the provider record the evidence will be attached to
     * @return an unsaved verification record holding the complete result
     */
    public VerificationRecord verify(ExtractedProviderData data, Provider provider) {
        long start = System.currentTimeMillis();

        VerificationRecord record = new VerificationRecord();
        record.setProvider(provider);
        StringBuilder notes = new StringBuilder();

        NppesRecord registry = resolveRegistryRecord(data.getNpi(), record, notes);
        List<Discrepancy> discrepancies = registry == null
                ? List.of()
                : compareWithRegistry(data, registry);

        applyRegistryFields(record, registry);
        writeDiscrepancies(record, discrepancies, data);
        screenForExclusion(data, record, notes);

        record.setProcessingNote(notes.length() == 0 ? null : notes.toString().strip());
        record.setDurationMs(System.currentTimeMillis() - start);
        return record;
    }

    /**
     * Determines the NPI status and retrieves the registry record, falling back
     * to the bundled snapshot when the live registry cannot be reached.
     *
     * @param npi    identifier read from the document, may be null
     * @param record verification record whose status fields are set here
     * @param notes  buffer that accumulates the processing explanation
     * @return the registry record, or null when none was obtained
     */
    private NppesRecord resolveRegistryRecord(String npi, VerificationRecord record, StringBuilder notes) {
        if (npi == null || npi.isBlank()) {
            record.setNpiStatus(NpiVerificationStatus.MISSING);
            record.setSource(VerificationSource.NONE);
            notes.append("No NPI was present in the document. ");
            return null;
        }

        if (!NpiValidator.isValid(npi)) {
            // The number cannot be an NPI, so there is nothing to look up.
            record.setNpiStatus(NpiVerificationStatus.INVALID_FORMAT);
            record.setSource(VerificationSource.NONE);
            notes.append("The identifier failed the CMS check-digit test, so no registry lookup was made. ");
            return null;
        }

        try {
            Optional<NppesRecord> live = nppesClient.lookupLive(npi);
            record.setSource(VerificationSource.LIVE_API);
            if (live.isEmpty()) {
                record.setNpiStatus(NpiVerificationStatus.NOT_FOUND);
                notes.append("The registry responded but holds no record for this identifier. ");
                return null;
            }
            NppesRecord found = live.get();
            record.setNpiStatus(found.isActive()
                    ? NpiVerificationStatus.VALID
                    : NpiVerificationStatus.DEACTIVATED);
            return found;

        } catch (NppesClient.RegistryUnavailableException ex) {
            log.warn("Falling back to the offline registry snapshot for NPI {}: {}", npi, ex.getMessage());
            Optional<NppesRecord> cached = nppesClient.lookupOffline(npi);

            if (cached.isPresent()) {
                record.setSource(VerificationSource.OFFLINE_FALLBACK);
                NppesRecord found = cached.get();
                record.setNpiStatus(found.isActive()
                        ? NpiVerificationStatus.VALID
                        : NpiVerificationStatus.DEACTIVATED);
                notes.append("The live registry was unreachable; this result came from the bundled "
                        + "offline snapshot and should be reconfirmed before a final decision. ");
                return found;
            }

            record.setSource(VerificationSource.NONE);
            record.setNpiStatus(NpiVerificationStatus.UNAVAILABLE);
            notes.append("The live registry was unreachable and the offline snapshot holds no record "
                    + "for this identifier. ");
            return null;
        }
    }

    /** Copies the registry's own values onto the record for side-by-side display. */
    private void applyRegistryFields(VerificationRecord record, NppesRecord registry) {
        if (registry == null) {
            return;
        }
        record.setRegistryName(registry.displayName());
        record.setRegistryAddress(registry.fullAddress());
        record.setRegistryTaxonomy(registry.primaryTaxonomy());
        record.setRegistryEnumerationDate(registry.enumerationDate());
        record.setApiResponse(registry.rawJson());
    }

    /**
     * Compares the document against the registry field by field.
     *
     * <p>Only fields that both sources publish are compared, and each comparison
     * uses the canonical form produced by the cleansing service, so "PHOENIX" and
     * "Phoenix" agree while "Phoenix" and "Mesa" do not.</p>
     *
     * @param data     cleansed document fields
     * @param registry the registry record
     * @return the discrepancies found, possibly empty
     */
    private List<Discrepancy> compareWithRegistry(ExtractedProviderData data, NppesRecord registry) {
        List<Discrepancy> discrepancies = new ArrayList<>();

        String documentName = data.getOrganizationName() != null
                ? data.getOrganizationName()
                : joinName(data.getLastName(), data.getFirstName());
        String registryName = registry.displayName();

        if (notBlank(documentName) && notBlank(registryName)) {
            String documentKey = cleansingService.comparisonKey(documentName);
            String registryKey = cleansingService.comparisonKey(registryName);
            // Names are compared by similarity rather than equality because
            // middle initials and suffixes legitimately differ between sources.
            if (StringSimilarity.jaroWinkler(documentKey, registryKey) < 0.90) {
                discrepancies.add(new Discrepancy("Provider name", documentName, registryName,
                        Discrepancy.SEVERITY_HIGH));
            }
        }

        if (data.getNpiType() != NpiType.UNKNOWN && registry.npiType() != NpiType.UNKNOWN
                && data.getNpiType() != registry.npiType()) {
            discrepancies.add(new Discrepancy("NPI entity type",
                    data.getNpiType().name(), registry.npiType().name(), Discrepancy.SEVERITY_HIGH));
        }

        compareCleansed(discrepancies, "Practice address",
                data.getAddressLine1(), registry.addressLine1(),
                cleansingService::normaliseAddress, Discrepancy.SEVERITY_MEDIUM);

        compareCleansed(discrepancies, "City",
                data.getCity(), registry.city(),
                cleansingService::normaliseAddress, Discrepancy.SEVERITY_MEDIUM);

        compareCleansed(discrepancies, "State",
                data.getState(), registry.state(),
                cleansingService::normaliseState, Discrepancy.SEVERITY_MEDIUM);

        compareCleansed(discrepancies, "Postal code",
                data.getPostalCode(), registry.postalCode(),
                cleansingService::normalisePostalCode, Discrepancy.SEVERITY_LOW);

        compareCleansed(discrepancies, "License number",
                data.getLicenseNumber(), registry.taxonomyLicense(),
                cleansingService::normaliseLicenseNumber, Discrepancy.SEVERITY_MEDIUM);

        return discrepancies;
    }

    /**
     * Compares two values after applying the same normalisation to both, adding a
     * discrepancy only when both sources published a value and the normalised
     * forms differ.
     *
     * @param target        list that receives any discrepancy found
     * @param field         display name of the field
     * @param documentValue value from the document
     * @param registryValue value from the registry
     * @param normaliser    the cleansing rule to apply to both values
     * @param severity      severity to record if they differ
     */
    private void compareCleansed(List<Discrepancy> target, String field,
                                 String documentValue, String registryValue,
                                 java.util.function.UnaryOperator<String> normaliser,
                                 String severity) {
        if (!notBlank(documentValue) || !notBlank(registryValue)) {
            return; // A field absent from either source is a gap, not a conflict.
        }
        String left = normaliser.apply(documentValue);
        String right = normaliser.apply(registryValue);
        if (left == null || right == null) {
            return;
        }
        if (!left.equalsIgnoreCase(right)) {
            target.add(new Discrepancy(field, documentValue, registryValue, severity));
        }
    }

    /** Serialises the discrepancy list and records the counts used for scoring. */
    private void writeDiscrepancies(VerificationRecord record, List<Discrepancy> discrepancies,
                                    ExtractedProviderData data) {
        record.setDiscrepancyCount(discrepancies.size());
        record.setHighSeverityDiscrepancyCount((int) discrepancies.stream()
                .filter(d -> Discrepancy.SEVERITY_HIGH.equals(d.severity()))
                .count());
        record.setMissingFieldCount(data.getMissingFields().size());
        try {
            record.setDiscrepanciesJson(objectMapper.writeValueAsString(discrepancies));
        } catch (Exception ex) {
            log.warn("Unable to serialise discrepancies: {}", ex.toString());
            record.setDiscrepanciesJson("[]");
        }
    }

    /** Runs the OIG screen and records its outcome on the verification record. */
    private void screenForExclusion(ExtractedProviderData data, VerificationRecord record,
                                    StringBuilder notes) {
        String name = data.getOrganizationName() != null
                ? data.getOrganizationName()
                : joinName(data.getLastName(), data.getFirstName());

        OigExclusionService.ExclusionScreeningResult screening =
                exclusionService.screen(data.getNpi(), name);

        record.setExclusionStatus(screening.status());
        record.setExclusionMatchScore(screening.matchScore());
        if (screening.match() != null) {
            record.setExclusionMatchedName(screening.match().displayName());
            record.setExclusionDate(screening.match().exclusionDate());
            record.setExclusionType(screening.match().exclusionType());
        }
        if (screening.note() != null) {
            notes.append(screening.note()).append(' ');
        }
    }

    /** Builds a "Last, First" display name, tolerating either part being absent. */
    private String joinName(String last, String first) {
        String lastPart = last == null ? "" : last;
        String firstPart = first == null ? "" : first;
        String joined = (lastPart + " " + firstPart).trim();
        return joined.isEmpty() ? null : joined;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
