package edu.gcu.capstone.credentialing.web;

import edu.gcu.capstone.credentialing.audit.AuditService;
import edu.gcu.capstone.credentialing.domain.RiskAssessment;
import edu.gcu.capstone.credentialing.exception.ResourceNotFoundException;
import edu.gcu.capstone.credentialing.ml.AnomalyDetectionService;
import edu.gcu.capstone.credentialing.ml.ModelEvaluation;
import edu.gcu.capstone.credentialing.repository.RiskAssessmentRepository;
import edu.gcu.capstone.credentialing.security.UserSession;
import edu.gcu.capstone.credentialing.web.dto.DecisionRequest;
import edu.gcu.capstone.credentialing.web.dto.RiskDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RiskController.java
 *
 * <p>Risk assessment retrieval, reviewer decisions, and the model quality figures
 * shown in the explainability panel.</p>
 *
 * <p>Endpoints: {@code GET /api/risk/{id}},
 * {@code POST /api/risk/{id}/decision}, {@code GET /api/risk/model}.</p>
 */
@RestController
@RequestMapping("/api/risk")
public class RiskController {

    private final RiskAssessmentRepository riskRepository;
    private final AnomalyDetectionService anomalyDetectionService;
    private final AuditService auditService;
    private final DtoMapper mapper;

    public RiskController(RiskAssessmentRepository riskRepository,
                          AnomalyDetectionService anomalyDetectionService,
                          AuditService auditService,
                          DtoMapper mapper) {
        this.riskRepository = riskRepository;
        this.anomalyDetectionService = anomalyDetectionService;
        this.auditService = auditService;
        this.mapper = mapper;
    }

    /**
     * Returns one risk assessment.
     *
     * @param id the assessment identifier
     * @return the assessment, its flags and its feature snapshot
     */
    @GetMapping("/{id}")
    public RiskDto get(@PathVariable Long id) {
        RiskAssessment assessment = riskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk assessment", id));
        return mapper.toRiskDto(assessment);
    }

    /**
     * Records a reviewer's decision against an assessment.
     *
     * <p>The decision is stored beside the platform's recommendation rather than
     * replacing it, so the record shows both what was advised and what a person
     * decided.</p>
     *
     * @param id      the assessment identifier
     * @param body    the decision and the reviewer's note
     * @param request the inbound request
     * @return the updated assessment
     */
    @PostMapping("/{id}/decision")
    public RiskDto recordDecision(@PathVariable Long id,
                                  @Valid @RequestBody DecisionRequest body,
                                  HttpServletRequest request) {
        UserSession session = ControllerSupport.currentSession(request);

        RiskAssessment assessment = riskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk assessment", id));

        assessment.setReviewerDecision(body.decision());
        assessment.setReviewedBy(session.getUsername());
        assessment.setReviewedAt(Instant.now());
        riskRepository.save(assessment);

        auditService.recordForEntity(session.getUsername(), session.getRole().name(),
                "DECISION_RECORDED", "REVIEW", "RISK_ASSESSMENT", assessment.getId(),
                "Reviewer recorded " + body.decision()
                        + " against a platform recommendation of " + assessment.getRecommendation()
                        + (body.note() == null || body.note().isBlank() ? "" : ". Note supplied."),
                "SUCCESS", ControllerSupport.clientAddress(request));

        return mapper.toRiskDto(assessment);
    }

    /**
     * Returns the anomaly model's measured quality.
     *
     * @return precision, recall, F1, accuracy and the confusion matrix
     */
    @GetMapping("/model")
    public Map<String, Object> modelQuality() {
        ModelEvaluation evaluation = anomalyDetectionService.getEvaluation();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modelVersion", anomalyDetectionService.getModelVersion());
        body.put("threshold", anomalyDetectionService.getThreshold());
        body.put("precision", round(evaluation.precision()));
        body.put("recall", round(evaluation.recall()));
        body.put("f1Score", round(evaluation.f1Score()));
        body.put("accuracy", round(evaluation.accuracy()));
        body.put("truePositives", evaluation.truePositives());
        body.put("falsePositives", evaluation.falsePositives());
        body.put("trueNegatives", evaluation.trueNegatives());
        body.put("falseNegatives", evaluation.falseNegatives());
        body.put("sampleCount", evaluation.sampleCount());
        return body;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
