package edu.gcu.capstone.credentialing.web;

import edu.gcu.capstone.credentialing.web.dto.ProviderDetailDto;
import edu.gcu.capstone.credentialing.web.dto.ProviderSummaryDto;
import edu.gcu.capstone.credentialing.web.dto.UploadResponseDto;
import edu.gcu.capstone.credentialing.workflow.CredentialingWorkflowService;
import edu.gcu.capstone.credentialing.workflow.ProviderQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ProviderController.java
 *
 * <p>Provider queue, provider detail and re-verification.</p>
 *
 * <p>Endpoints: {@code GET /api/providers}, {@code GET /api/providers/{id}},
 * {@code POST /api/providers/{id}/reverify}.</p>
 */
@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderQueryService queryService;
    private final CredentialingWorkflowService workflowService;

    public ProviderController(ProviderQueryService queryService,
                              CredentialingWorkflowService workflowService) {
        this.queryService = queryService;
        this.workflowService = workflowService;
    }

    /**
     * Lists providers for the verification queue.
     *
     * @param status optional workflow status filter
     * @param search optional free-text search across NPI and names
     * @return matching providers, newest first
     */
    @GetMapping
    public List<ProviderSummaryDto> list(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String search) {
        return queryService.listProviders(status, search);
    }

    /**
     * Returns one provider with its documents, evidence and risk history.
     *
     * @param id the provider identifier
     * @return the detail response
     */
    @GetMapping("/{id}")
    public ProviderDetailDto detail(@PathVariable Long id) {
        return queryService.getProviderDetail(id);
    }

    /**
     * Re-runs verification and scoring for a provider already on file, without
     * requiring a new document.
     *
     * @param id      the provider identifier
     * @param request the inbound request
     * @return the refreshed outcome
     */
    @PostMapping("/{id}/reverify")
    public UploadResponseDto reverify(@PathVariable Long id, HttpServletRequest request) {
        CredentialingWorkflowService.ProcessingResult result = workflowService.reverify(
                id, ControllerSupport.currentSession(request), ControllerSupport.clientAddress(request));

        return new UploadResponseDto(
                result.provider().getId(),
                null,
                result.provider().getDisplayName(),
                result.provider().getNpi(),
                result.provider().getStatus().name(),
                result.verification() == null ? null : result.verification().getNpiStatus().name(),
                result.verification() == null ? null : result.verification().getExclusionStatus().name(),
                result.assessment() == null ? null : result.assessment().getScore(),
                result.assessment() == null ? null : result.assessment().getRecommendation().name(),
                result.elapsedMs(),
                result.warning());
    }
}
