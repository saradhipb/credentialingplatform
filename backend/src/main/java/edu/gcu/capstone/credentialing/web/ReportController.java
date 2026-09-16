package edu.gcu.capstone.credentialing.web;

import edu.gcu.capstone.credentialing.report.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * ReportController.java
 *
 * <p>Report downloads. Each response carries a {@code Content-Disposition} header
 * with a descriptive file name so the downloaded file is identifiable once it
 * leaves the platform.</p>
 *
 * <p>Endpoints: {@code GET /api/reports/verification/{providerId}},
 * {@code GET /api/reports/risk/{providerId}}, {@code GET /api/reports/audit},
 * {@code GET /api/reports/queue.csv}.</p>
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Downloads the Credential Verification Report for a provider.
     *
     * @param providerId the provider to report on
     * @param request    the inbound request
     * @return a PDF attachment
     * @throws IOException if the document cannot be written
     */
    @GetMapping("/verification/{providerId}")
    public ResponseEntity<byte[]> verificationReport(@PathVariable Long providerId,
                                                     HttpServletRequest request) throws IOException {
        byte[] pdf = reportService.credentialVerificationReport(providerId,
                ControllerSupport.currentSession(request).getUsername());
        return pdfResponse(pdf, "credential-verification-" + providerId + ".pdf");
    }

    /**
     * Downloads the Risk Analysis Report for a provider.
     *
     * @param providerId the provider to report on
     * @param request    the inbound request
     * @return a PDF attachment
     * @throws IOException if the document cannot be written
     */
    @GetMapping("/risk/{providerId}")
    public ResponseEntity<byte[]> riskReport(@PathVariable Long providerId,
                                             HttpServletRequest request) throws IOException {
        byte[] pdf = reportService.riskAnalysisReport(providerId,
                ControllerSupport.currentSession(request).getUsername());
        return pdfResponse(pdf, "risk-analysis-" + providerId + ".pdf");
    }

    /**
     * Downloads the Audit Log Report.
     *
     * @param request the inbound request
     * @return a PDF attachment
     * @throws IOException if the document cannot be written
     */
    @GetMapping("/audit")
    public ResponseEntity<byte[]> auditReport(HttpServletRequest request) throws IOException {
        byte[] pdf = reportService.auditLogReport(
                ControllerSupport.currentSession(request).getUsername());
        return pdfResponse(pdf, "audit-log.pdf");
    }

    /**
     * Downloads the verification queue as CSV.
     *
     * @param request the inbound request
     * @return a CSV attachment
     */
    @GetMapping("/queue.csv")
    public ResponseEntity<byte[]> queueCsv(HttpServletRequest request) {
        byte[] csv = reportService.providerQueueCsv(
                ControllerSupport.currentSession(request).getUsername());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"verification-queue.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    /** Wraps PDF bytes in a download response. */
    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
