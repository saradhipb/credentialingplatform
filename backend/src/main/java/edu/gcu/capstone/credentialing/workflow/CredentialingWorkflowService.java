package edu.gcu.capstone.credentialing.workflow;

import edu.gcu.capstone.credentialing.audit.AuditService;
import edu.gcu.capstone.credentialing.domain.*;
import edu.gcu.capstone.credentialing.extraction.ExtractedProviderData;
import edu.gcu.capstone.credentialing.extraction.ProviderDataExtractor;
import edu.gcu.capstone.credentialing.ml.AnomalyDetectionService;
import edu.gcu.capstone.credentialing.ml.FeatureExtractor;
import edu.gcu.capstone.credentialing.ml.FeatureVector;
import edu.gcu.capstone.credentialing.ml.RiskScoringService;
import edu.gcu.capstone.credentialing.ocr.OcrResult;
import edu.gcu.capstone.credentialing.ocr.TextExtractionService;
import edu.gcu.capstone.credentialing.repository.CredentialDocumentRepository;
import edu.gcu.capstone.credentialing.repository.ProviderRepository;
import edu.gcu.capstone.credentialing.repository.RiskAssessmentRepository;
import edu.gcu.capstone.credentialing.repository.VerificationRecordRepository;
import edu.gcu.capstone.credentialing.security.UserSession;
import edu.gcu.capstone.credentialing.verification.VerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;

/**
 * CredentialingWorkflowService.java
 *
 * <p>Orchestrates the end-to-end credentialing pipeline described in Milestone 2:
 * upload, text extraction, field parsing and cleansing, verification against the
 * authoritative sources, anomaly detection, risk scoring, and persistence.</p>
 *
 * <p>Each stage lives in its own service; this class only sequences them and
 * decides what happens when a stage cannot complete. Keeping the sequence in one
 * readable method is intentional, because the order of these steps is the part of
 * the design a reviewer most needs to be able to follow.</p>
 *
 * <p>The whole sequence runs in one transaction, so a failure part-way through
 * leaves no half-processed provider behind. The audit entries are the deliberate
 * exception: they are written in their own transactions and survive a rollback.</p>
 */
@Service
public class CredentialingWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(CredentialingWorkflowService.class);

    private final DocumentStorageService storageService;
    private final TextExtractionService textExtractionService;
    private final ProviderDataExtractor dataExtractor;
    private final VerificationService verificationService;
    private final FeatureExtractor featureExtractor;
    private final AnomalyDetectionService anomalyDetectionService;
    private final RiskScoringService riskScoringService;
    private final ProviderRepository providerRepository;
    private final CredentialDocumentRepository documentRepository;
    private final VerificationRecordRepository verificationRepository;
    private final RiskAssessmentRepository riskRepository;
    private final AuditService auditService;

    public CredentialingWorkflowService(DocumentStorageService storageService,
                                        TextExtractionService textExtractionService,
                                        ProviderDataExtractor dataExtractor,
                                        VerificationService verificationService,
                                        FeatureExtractor featureExtractor,
                                        AnomalyDetectionService anomalyDetectionService,
                                        RiskScoringService riskScoringService,
                                        ProviderRepository providerRepository,
                                        CredentialDocumentRepository documentRepository,
                                        VerificationRecordRepository verificationRepository,
                                        RiskAssessmentRepository riskRepository,
                                        AuditService auditService) {
        this.storageService = storageService;
        this.textExtractionService = textExtractionService;
        this.dataExtractor = dataExtractor;
        this.verificationService = verificationService;
        this.featureExtractor = featureExtractor;
        this.anomalyDetectionService = anomalyDetectionService;
        this.riskScoringService = riskScoringService;
        this.providerRepository = providerRepository;
        this.documentRepository = documentRepository;
        this.verificationRepository = verificationRepository;
        this.riskRepository = riskRepository;
        this.auditService = auditService;
    }

    /**
     * Runs the complete credentialing pipeline for one uploaded document.
     *
     * @param file         the uploaded credential document
     * @param documentType free-text classification chosen on the upload form
     * @param session      the signed-in user performing the upload
     * @param ipAddress    the caller's address, recorded in the audit trail
     * @return the provider, document, verification and risk assessment produced
     */
    @Transactional
    public ProcessingResult process(MultipartFile file, String documentType,
                                    UserSession session, String ipAddress) {

        long pipelineStart = System.currentTimeMillis();

        // ---- Stage 1: store the submitted file -----------------------------
        String storedName = storageService.store(file);
        CredentialDocument document = new CredentialDocument();
        document.setOriginalFilename(file.getOriginalFilename());
        document.setStoredFilename(storedName);
        document.setContentType(file.getContentType());
        document.setSizeBytes(file.getSize());
        document.setDocumentType(documentType);
        document.setUploadedBy(session.getUsername());

        auditService.record(session.getUsername(), session.getRole().name(), "DOCUMENT_UPLOAD",
                "INTAKE", "Received " + file.getOriginalFilename(), "SUCCESS", ipAddress);

        // ---- Stage 2: extract text ------------------------------------------
        Path stored = storageService.pathOf(storedName);
        OcrResult extraction = textExtractionService.extract(stored, file.getContentType());
        document.setExtractedText(extraction.text());
        document.setExtractionEngine(extraction.engine());
        document.setExtractionConfidence(extraction.confidence());
        document.setExtractionMillis(extraction.millis());
        document.setStatus(extraction.hasText() ? DocumentStatus.EXTRACTED
                : DocumentStatus.EXTRACTION_FAILED);

        // ---- Stage 3: parse and cleanse provider fields ---------------------
        ExtractedProviderData data = dataExtractor.extract(extraction.text());
        if (extraction.hasText() && data.isUsable()) {
            document.setStatus(DocumentStatus.PARSED);
        }

        // ---- Stage 4: attach to a provider record ---------------------------
        Provider provider = resolveProvider(data);
        document.setProvider(provider);
        provider.getDocuments().add(document);

        // A document nothing could be read from must not be presented as a
        // verified provider with empty fields; it goes to the review queue.
        if (!extraction.hasText() || !data.isUsable()) {
            provider.setStatus(ProviderStatus.MANUAL_REVIEW);
            providerRepository.save(provider);
            documentRepository.save(document);

            auditService.recordForEntity(session.getUsername(), session.getRole().name(),
                    "EXTRACTION_FAILED", "INTAKE", "PROVIDER", provider.getId(),
                    "No usable provider data could be read; routed to manual review",
                    "FAILURE", ipAddress);

            log.info("Document {} produced no usable data and was routed to manual review",
                    file.getOriginalFilename());
            return new ProcessingResult(provider, document, null, null,
                    System.currentTimeMillis() - pipelineStart,
                    extraction.note() == null
                            ? "No readable text could be extracted from this document."
                            : extraction.note());
        }

        // ---- Stage 5: verify against the authoritative sources --------------
        VerificationRecord verification = verificationService.verify(data, provider);
        verification.setDocument(document);

        auditService.recordForEntity(session.getUsername(), session.getRole().name(),
                "VERIFICATION_RUN", "VERIFICATION", "PROVIDER", provider.getId(),
                "NPI status " + verification.getNpiStatus()
                        + ", exclusion status " + verification.getExclusionStatus()
                        + ", source " + verification.getSource(),
                "SUCCESS", ipAddress);

        // ---- Stage 6: score the event ---------------------------------------
        String documentName = data.getOrganizationName() != null
                ? data.getOrganizationName()
                : joinName(data.getLastName(), data.getFirstName());

        FeatureVector features = featureExtractor.extract(verification, document, documentName);
        AnomalyDetectionService.AnomalyResult anomaly = anomalyDetectionService.score(features);
        RiskAssessment assessment = riskScoringService.score(verification, anomaly, features);

        // ---- Stage 7: settle the provider's workflow state ------------------
        provider.setStatus(deriveStatus(verification, assessment));
        provider.setUpdatedAt(Instant.now());

        // ---- Stage 8: persist ------------------------------------------------
        providerRepository.save(provider);
        documentRepository.save(document);
        verificationRepository.save(verification);
        assessment.setVerificationRecord(verification);
        riskRepository.save(assessment);

        long elapsed = System.currentTimeMillis() - pipelineStart;
        auditService.recordForEntity(session.getUsername(), session.getRole().name(),
                "RISK_ASSESSED", "INTELLIGENCE", "PROVIDER", provider.getId(),
                String.format("Risk score %.1f, recommendation %s, completed in %d ms",
                        assessment.getScore(), assessment.getRecommendation(), elapsed),
                "SUCCESS", ipAddress);

        log.info("Processed {} for provider {} in {} ms: {} / risk {}",
                file.getOriginalFilename(), provider.getDisplayName(), elapsed,
                provider.getStatus(), assessment.getScore());

        return new ProcessingResult(provider, document, verification, assessment, elapsed, null);
    }

    /**
     * Re-runs verification and scoring for a provider already on file, without
     * requiring a new document. This is the re-verification path used when a
     * registry was unreachable or when a periodic recheck is due.
     *
     * @param providerId the provider to reverify
     * @param session    the signed-in user
     * @param ipAddress  the caller's address
     * @return the refreshed evidence and score
     */
    @Transactional
    public ProcessingResult reverify(Long providerId, UserSession session, String ipAddress) {
        long start = System.currentTimeMillis();

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new edu.gcu.capstone.credentialing.exception
                        .ResourceNotFoundException("Provider", providerId));

        // Rebuild the parsed-field view from the provider record itself, which is
        // the cleansed result of the original extraction.
        ExtractedProviderData data = new ExtractedProviderData();
        data.setNpi(provider.getNpi());
        data.setNpiType(provider.getNpiType());
        data.setFirstName(provider.getFirstName());
        data.setLastName(provider.getLastName());
        data.setOrganizationName(provider.getOrganizationName());
        data.setAddressLine1(provider.getAddressLine1());
        data.setCity(provider.getCity());
        data.setState(provider.getState());
        data.setPostalCode(provider.getPostalCode());
        data.setLicenseNumber(provider.getLicenseNumber());

        VerificationRecord verification = verificationService.verify(data, provider);
        FeatureVector features = featureExtractor.extract(verification, null, provider.getDisplayName());
        AnomalyDetectionService.AnomalyResult anomaly = anomalyDetectionService.score(features);
        RiskAssessment assessment = riskScoringService.score(verification, anomaly, features);

        provider.setStatus(deriveStatus(verification, assessment));
        provider.setUpdatedAt(Instant.now());

        providerRepository.save(provider);
        verificationRepository.save(verification);
        assessment.setVerificationRecord(verification);
        riskRepository.save(assessment);

        long elapsed = System.currentTimeMillis() - start;
        auditService.recordForEntity(session.getUsername(), session.getRole().name(),
                "REVERIFICATION", "VERIFICATION", "PROVIDER", provider.getId(),
                "Re-verified against NPI and OIG sources", "SUCCESS", ipAddress);

        return new ProcessingResult(provider, null, verification, assessment, elapsed, null);
    }

    /**
     * Finds the existing provider for the extracted identifier, or creates one.
     *
     * <p>Matching on NPI keeps a provider's documents, verifications and risk
     * history on a single record, which is what makes the time-based risk history
     * in the data model meaningful.</p>
     *
     * @param data the cleansed document fields
     * @return a managed or newly created provider, with fields refreshed
     */
    private Provider resolveProvider(ExtractedProviderData data) {
        Provider provider = null;
        if (data.getNpi() != null && !data.getNpi().isBlank()) {
            provider = providerRepository.findByNpi(data.getNpi()).orElse(null);
        }
        if (provider == null) {
            provider = new Provider();
            provider.setNpi(data.getNpi());
        }

        // Only overwrite a stored field when the new document actually supplies a
        // value, so a partial submission cannot erase details already on file.
        if (data.getFirstName() != null) {
            provider.setFirstName(data.getFirstName());
        }
        if (data.getLastName() != null) {
            provider.setLastName(data.getLastName());
        }
        if (data.getOrganizationName() != null) {
            provider.setOrganizationName(data.getOrganizationName());
        }
        if (data.getAddressLine1() != null) {
            provider.setAddressLine1(data.getAddressLine1());
        }
        if (data.getCity() != null) {
            provider.setCity(data.getCity());
        }
        if (data.getState() != null) {
            provider.setState(data.getState());
        }
        if (data.getPostalCode() != null) {
            provider.setPostalCode(data.getPostalCode());
        }
        if (data.getLicenseNumber() != null) {
            provider.setLicenseNumber(data.getLicenseNumber());
        }
        if (data.getLicenseState() != null) {
            provider.setLicenseState(data.getLicenseState());
        }
        if (data.getTaxonomy() != null) {
            provider.setTaxonomy(data.getTaxonomy());
        }
        if (data.getNpiType() != NpiType.UNKNOWN) {
            provider.setNpiType(data.getNpiType());
        }
        return provider;
    }

    /**
     * Translates the evidence and the score into the provider's workflow state.
     *
     * @param verification the verification evidence
     * @param assessment   the risk assessment
     * @return the state to store on the provider
     */
    private ProviderStatus deriveStatus(VerificationRecord verification, RiskAssessment assessment) {
        if (assessment.getRecommendation() == RiskRecommendation.REJECT) {
            return ProviderStatus.REJECTED;
        }
        // An unreachable source is an unfinished check, not a clean result.
        if (verification.getNpiStatus() == NpiVerificationStatus.UNAVAILABLE
                || verification.getExclusionStatus() == ExclusionStatus.UNAVAILABLE) {
            return ProviderStatus.MANUAL_REVIEW;
        }
        if (assessment.getRecommendation() == RiskRecommendation.REVIEW) {
            return ProviderStatus.FLAGGED;
        }
        return ProviderStatus.ACTIVE;
    }

    private String joinName(String last, String first) {
        String lastPart = last == null ? "" : last;
        String firstPart = first == null ? "" : first;
        String joined = (lastPart + " " + firstPart).trim();
        return joined.isEmpty() ? null : joined;
    }

    /**
     * Everything one pipeline run produced.
     *
     * @param provider     the provider record, created or updated
     * @param document     the stored document, null on a re-verification
     * @param verification the verification evidence, null when extraction failed
     * @param assessment   the risk assessment, null when extraction failed
     * @param elapsedMs    total pipeline duration
     * @param warning      a message to show the user, or null
     */
    public record ProcessingResult(Provider provider, CredentialDocument document,
                                   VerificationRecord verification, RiskAssessment assessment,
                                   long elapsedMs, String warning) {
    }
}
