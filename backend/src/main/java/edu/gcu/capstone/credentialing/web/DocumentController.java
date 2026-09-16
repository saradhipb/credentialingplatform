package edu.gcu.capstone.credentialing.web;

import edu.gcu.capstone.credentialing.workflow.CredentialingWorkflowService;
import edu.gcu.capstone.credentialing.web.dto.UploadResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * DocumentController.java
 *
 * <p>Document intake. A single request uploads the file and runs the complete
 * pipeline, returning the outcome, so the user sees the verification result on
 * the same screen where they submitted the document rather than having to go
 * looking for it.</p>
 *
 * <p>Endpoint: {@code POST /api/documents/upload} (multipart).</p>
 *
 * <p>This controller satisfies functional requirement FR-01 (upload and manage
 * credential documents).</p>
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final CredentialingWorkflowService workflowService;

    public DocumentController(CredentialingWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /**
     * Uploads and processes one credential document.
     *
     * @param file         the uploaded file
     * @param documentType classification chosen on the upload form
     * @param request      the inbound request
     * @return the processing outcome
     */
    @PostMapping("/upload")
    public UploadResponseDto upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "documentType", required = false,
                                            defaultValue = "Credential document") String documentType,
                                    HttpServletRequest request) {

        CredentialingWorkflowService.ProcessingResult result = workflowService.process(
                file, documentType,
                ControllerSupport.currentSession(request),
                ControllerSupport.clientAddress(request));

        return new UploadResponseDto(
                result.provider().getId(),
                result.document() == null ? null : result.document().getId(),
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
