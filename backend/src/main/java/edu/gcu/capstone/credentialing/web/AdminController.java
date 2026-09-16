package edu.gcu.capstone.credentialing.web;

import edu.gcu.capstone.credentialing.audit.AuditService;
import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import edu.gcu.capstone.credentialing.ml.AnomalyDetectionService;
import edu.gcu.capstone.credentialing.ml.ModelEvaluation;
import edu.gcu.capstone.credentialing.ocr.TextExtractionService;
import edu.gcu.capstone.credentialing.repository.AppUserRepository;
import edu.gcu.capstone.credentialing.security.SessionRegistry;
import edu.gcu.capstone.credentialing.verification.NppesClient;
import edu.gcu.capstone.credentialing.verification.OigExclusionService;
import edu.gcu.capstone.credentialing.web.dto.SystemHealthDto;
import edu.gcu.capstone.credentialing.web.dto.UserDto;
import edu.gcu.capstone.credentialing.workflow.DocumentRetentionService;
import edu.gcu.capstone.credentialing.workflow.DocumentStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AdminController.java
 *
 * <p>The Administrator Control Panel: integration health, model quality, account
 * management and the manual retention trigger. Every endpoint under
 * {@code /api/admin} is restricted to the ADMIN role by
 * {@code AuthorizationFilter}.</p>
 *
 * <p>Endpoints: {@code GET /api/admin/health}, {@code GET /api/admin/users},
 * {@code GET /api/admin/sessions}, {@code POST /api/admin/retention/purge}.</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ApplicationProperties properties;
    private final NppesClient nppesClient;
    private final OigExclusionService exclusionService;
    private final TextExtractionService textExtractionService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final DocumentStorageService storageService;
    private final DocumentRetentionService retentionService;
    private final AppUserRepository userRepository;
    private final SessionRegistry sessionRegistry;
    private final AuditService auditService;
    private final DtoMapper mapper;

    public AdminController(ApplicationProperties properties,
                           NppesClient nppesClient,
                           OigExclusionService exclusionService,
                           TextExtractionService textExtractionService,
                           AnomalyDetectionService anomalyDetectionService,
                           DocumentStorageService storageService,
                           DocumentRetentionService retentionService,
                           AppUserRepository userRepository,
                           SessionRegistry sessionRegistry,
                           AuditService auditService,
                           DtoMapper mapper) {
        this.properties = properties;
        this.nppesClient = nppesClient;
        this.exclusionService = exclusionService;
        this.textExtractionService = textExtractionService;
        this.anomalyDetectionService = anomalyDetectionService;
        this.storageService = storageService;
        this.retentionService = retentionService;
        this.userRepository = userRepository;
        this.sessionRegistry = sessionRegistry;
        this.auditService = auditService;
        this.mapper = mapper;
    }

    /**
     * Reports integration and model health.
     *
     * @return the state of every external dependency and the trained model
     */
    @GetMapping("/health")
    public SystemHealthDto health() {
        ModelEvaluation evaluation = anomalyDetectionService.getEvaluation();
        return new SystemHealthDto(
                properties.getNppes().isEnabled(),
                nppesClient.isLiveRegistryReachable(),
                nppesClient.getLastLiveError(),
                nppesClient.offlineRecordCount(),
                exclusionService.isListLoaded(),
                exclusionService.recordCount(),
                textExtractionService.engineStatus(),
                anomalyDetectionService.getModelVersion(),
                anomalyDetectionService.getThreshold(),
                round(evaluation.precision()),
                round(evaluation.recall()),
                round(evaluation.f1Score()),
                round(evaluation.accuracy()),
                evaluation.sampleCount(),
                storageService.getUploadDirectory().toString(),
                properties.getStorage().getRetentionDays());
    }

    /**
     * Lists platform accounts.
     *
     * @return every account, without any password material
     */
    @GetMapping("/users")
    public List<UserDto> users() {
        return userRepository.findAll().stream().map(mapper::toUserDto).toList();
    }

    /**
     * Lists currently active sessions.
     *
     * @return one entry per live session
     */
    @GetMapping("/sessions")
    public List<Map<String, Object>> sessions() {
        return sessionRegistry.activeSessions().stream()
                .map(session -> Map.<String, Object>of(
                        "username", session.getUsername(),
                        "role", session.getRole().name(),
                        "openedAt", session.getCreatedAt().toString(),
                        "lastActivity", session.getLastAccessedAt().toString()))
                .toList();
    }

    /**
     * Runs the retention purge immediately instead of waiting for the nightly job.
     *
     * @param request the inbound request
     * @return how many stored binaries were removed
     */
    @PostMapping("/retention/purge")
    public Map<String, Object> purge(HttpServletRequest request) {
        int purged = retentionService.purgeExpiredDocuments();
        auditService.record(ControllerSupport.currentSession(request).getUsername(), "ADMIN",
                "RETENTION_PURGE_MANUAL", "STORAGE",
                "Administrator ran the retention purge manually", "SUCCESS",
                ControllerSupport.clientAddress(request));
        return Map.of("purgedDocuments", purged,
                "retentionDays", properties.getStorage().getRetentionDays());
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
