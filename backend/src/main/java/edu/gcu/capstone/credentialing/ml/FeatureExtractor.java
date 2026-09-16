package edu.gcu.capstone.credentialing.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.gcu.capstone.credentialing.domain.CredentialDocument;
import edu.gcu.capstone.credentialing.domain.VerificationRecord;
import edu.gcu.capstone.credentialing.extraction.DataCleansingService;
import edu.gcu.capstone.credentialing.verification.Discrepancy;
import edu.gcu.capstone.credentialing.verification.StringSimilarity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FeatureExtractor.java
 *
 * <p>Turns a completed verification into the {@link FeatureVector} the anomaly
 * detector consumes. Placing this mapping in one class keeps the definition of
 * "what the model sees" in a single, reviewable place, which matters because a
 * change here silently changes every score the platform produces.</p>
 */
@Component
public class FeatureExtractor {

    /** Discrepancy count at which the volume feature saturates at 1.0. */
    private static final double DISCREPANCY_SATURATION = 6.0;

    /** Number of fields the extractor attempts to find, used to scale the gap. */
    private static final double EXPECTED_FIELD_COUNT = 7.0;

    private final DataCleansingService cleansingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FeatureExtractor(DataCleansingService cleansingService) {
        this.cleansingService = cleansingService;
    }

    /**
     * Builds the feature vector for one verification event.
     *
     * @param verification the completed verification evidence
     * @param document     the source document, may be null when none applies
     * @param documentName the provider name as read from the document, may be null
     * @return the scaled feature vector
     */
    public FeatureVector extract(VerificationRecord verification, CredentialDocument document,
                                 String documentName) {
        double npiRisk = switch (verification.getNpiStatus()) {
            case VALID -> 0.00;
            case DEACTIVATED -> 0.55;
            case UNAVAILABLE -> 0.45;   // Uncertainty about the source, not the provider.
            case NOT_FOUND -> 0.85;
            case MISSING, INVALID_FORMAT -> 1.00;
        };

        double exclusionRisk = switch (verification.getExclusionStatus()) {
            case NOT_EXCLUDED -> 0.00;
            case UNAVAILABLE -> 0.40;
            case POSSIBLE_MATCH -> 0.75;
            case EXCLUDED -> 1.00;
        };

        double discrepancyVolume =
                Math.min(1.0, verification.getDiscrepancyCount() / DISCREPANCY_SATURATION);

        double discrepancySeverity = weightedSeverity(verification.getDiscrepanciesJson());

        double missingRatio =
                Math.min(1.0, verification.getMissingFieldCount() / EXPECTED_FIELD_COUNT);

        // Extraction confidence is inverted so that, like every other feature,
        // a larger number means a more concerning observation.
        double extractionUncertainty = document == null
                ? 0.5
                : Math.max(0.0, 1.0 - document.getExtractionConfidence());

        double nameGap = nameSimilarityGap(documentName, verification.getRegistryName());

        return new FeatureVector(npiRisk, exclusionRisk, discrepancyVolume,
                discrepancySeverity, missingRatio, extractionUncertainty, nameGap);
    }

    /**
     * Computes the severity-weighted discrepancy load from the stored JSON.
     *
     * @param discrepanciesJson serialised discrepancy array, may be null
     * @return a value in the range 0.0-1.0
     */
    private double weightedSeverity(String discrepanciesJson) {
        if (discrepanciesJson == null || discrepanciesJson.isBlank()) {
            return 0.0;
        }
        try {
            List<Discrepancy> discrepancies = objectMapper.readValue(
                    discrepanciesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Discrepancy.class));
            double total = discrepancies.stream().mapToDouble(Discrepancy::weight).sum();
            return Math.min(1.0, total / 3.0); // Three high-severity conflicts saturate.
        } catch (Exception ex) {
            return 0.0; // A malformed record must not be read as a strong signal.
        }
    }

    /**
     * Measures how far the document name sits from the registry name.
     *
     * @param documentName name read from the document
     * @param registryName name returned by the registry
     * @return 0.0 for identical names, 1.0 for completely dissimilar names, and
     *         0.5 when the comparison cannot be made
     */
    private double nameSimilarityGap(String documentName, String registryName) {
        if (documentName == null || documentName.isBlank()
                || registryName == null || registryName.isBlank()) {
            return 0.5; // Unknown, not benign.
        }
        double similarity = StringSimilarity.jaroWinkler(
                cleansingService.comparisonKey(documentName),
                cleansingService.comparisonKey(registryName));
        return Math.max(0.0, 1.0 - similarity);
    }
}
