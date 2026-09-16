package edu.gcu.capstone.credentialing.report;

import edu.gcu.capstone.credentialing.audit.AuditService;
import edu.gcu.capstone.credentialing.domain.AuditLog;
import edu.gcu.capstone.credentialing.repository.AuditLogRepository;
import edu.gcu.capstone.credentialing.web.dto.ProviderDetailDto;
import edu.gcu.capstone.credentialing.web.dto.ProviderSummaryDto;
import edu.gcu.capstone.credentialing.workflow.ProviderQueryService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ReportService.java
 *
 * <p>Produces the three reports specified in Milestone 1: the Credential
 * Verification Report, the Risk Analysis Report and the Audit Log Report.</p>
 *
 * <p>Each is available as a PDF for filing and as CSV for further analysis. Both
 * formats are generated from the same stored records, so an exported report can
 * never disagree with what the dashboard shows.</p>
 *
 * <p>This service satisfies functional requirements FR-07, FR-08 and FR-09.</p>
 */
@Service
public class ReportService {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'at' HH:mm z").withZone(ZoneId.systemDefault());

    private final ProviderQueryService queryService;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    public ReportService(ProviderQueryService queryService,
                         AuditLogRepository auditLogRepository,
                         AuditService auditService) {
        this.queryService = queryService;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
    }

    /**
     * Builds the Credential Verification Report for one provider.
     *
     * @param providerId  the provider to report on
     * @param generatedBy the account requesting the report
     * @return the rendered PDF
     * @throws IOException if the document cannot be written
     */
    public byte[] credentialVerificationReport(Long providerId, String generatedBy) throws IOException {
        ProviderDetailDto provider = queryService.getProviderDetail(providerId);

        try (PdfReportWriter writer = new PdfReportWriter()) {
            writer.title("Credential Verification Report",
                    "AI-Enhanced Provider Credentialing Automation Platform  |  Generated "
                            + TIMESTAMP.format(ZonedDateTime.now()) + " by " + generatedBy);

            writer.section("Provider");
            writer.field("Name", provider.displayName());
            writer.field("NPI", provider.npi());
            writer.field("Enumeration type", provider.npiType());
            writer.field("Practice address", joinAddress(provider));
            writer.field("License number", provider.licenseNumber());
            writer.field("Specialty", provider.taxonomy());
            writer.field("Workflow status", provider.status());

            writer.section("Verification against authoritative sources");
            if (provider.verification() == null) {
                writer.paragraph("No verification has been completed for this provider.");
            } else {
                var verification = provider.verification();
                writer.field("CMS NPI registry status", verification.npiStatus());
                writer.field("Registry name", verification.registryName());
                writer.field("Registry address", verification.registryAddress());
                writer.field("Registry enumeration date", verification.enumerationDate());
                writer.field("OIG exclusion status", verification.exclusionStatus());
                writer.field("Matched exclusion record", verification.exclusionMatchedName());
                writer.field("Exclusion date", verification.exclusionDate());
                writer.field("Exclusion basis", verification.exclusionType());
                writer.field("Evidence source", verification.source());
                writer.field("Verified at", verification.verifiedAt());
                writer.field("External check duration", verification.durationMs() + " ms");

                writer.section("Field comparison");
                if (verification.discrepancies().isEmpty()) {
                    writer.paragraph("No differences were found between the submitted document and the "
                            + "registry record for the fields both sources publish.");
                } else {
                    List<String[]> rows = new ArrayList<>();
                    verification.discrepancies().forEach(d ->
                            rows.add(new String[]{d.field(), d.documentValue(), d.registryValue(), d.severity()}));
                    writer.table(new String[]{"Field", "Document value", "Registry value", "Severity"},
                            rows, new float[]{120f, 150f, 150f, 70f});
                }
                if (verification.processingNote() != null) {
                    writer.section("Processing note");
                    writer.paragraph(verification.processingNote());
                }
            }

            writer.section("Attestation");
            writer.paragraph("This report records the result of automated verification against the CMS "
                    + "NPPES registry and the HHS OIG List of Excluded Individuals/Entities at the time "
                    + "stated above. It supports, and does not replace, the credentialing determination "
                    + "made by an authorized reviewer.");

            auditService.record(generatedBy, null, "REPORT_EXPORT", "REPORTING",
                    "Credential Verification Report generated for provider " + providerId,
                    "SUCCESS", null);
            return writer.toByteArray();
        }
    }

    /**
     * Builds the Risk Analysis Report for one provider.
     *
     * @param providerId  the provider to report on
     * @param generatedBy the account requesting the report
     * @return the rendered PDF
     * @throws IOException if the document cannot be written
     */
    public byte[] riskAnalysisReport(Long providerId, String generatedBy) throws IOException {
        ProviderDetailDto provider = queryService.getProviderDetail(providerId);

        try (PdfReportWriter writer = new PdfReportWriter()) {
            writer.title("Risk Analysis Report",
                    "AI-Enhanced Provider Credentialing Automation Platform  |  Generated "
                            + TIMESTAMP.format(ZonedDateTime.now()) + " by " + generatedBy);

            writer.section("Provider");
            writer.field("Name", provider.displayName());
            writer.field("NPI", provider.npi());
            writer.field("Workflow status", provider.status());

            if (provider.risk() == null) {
                writer.paragraph("No risk assessment has been produced for this provider.");
            } else {
                var risk = provider.risk();
                writer.section("Assessment");
                writer.field("Composite risk score (0-100)", String.format("%.1f", risk.score()));
                writer.field("Platform recommendation", risk.recommendation());
                writer.field("Anomaly score (0.00-1.00)", String.format("%.3f", risk.anomalyScore()));
                writer.field("Reported as anomalous", risk.anomalous() ? "Yes" : "No");
                writer.field("Model confidence", String.format("%.2f", risk.confidence()));
                writer.field("Model version", risk.modelVersion());
                writer.field("Assessed at", risk.assessedAt());
                writer.field("Reviewer decision", risk.reviewerDecision());
                writer.field("Decision recorded by", risk.reviewedBy());

                writer.section("Contributing findings");
                if (risk.flags().isEmpty()) {
                    writer.paragraph("No adverse findings were recorded.");
                } else {
                    for (String flag : risk.flags()) {
                        writer.paragraph("- " + flag);
                    }
                }

                writer.section("Feature values scored");
                writer.paragraph(risk.featureSnapshot() == null ? "-" : risk.featureSnapshot());
            }

            writer.section("Interpretation");
            writer.paragraph("The composite score combines exclusion screening (40 per cent), identifier "
                    + "verification (25 per cent), field discrepancies (20 per cent) and the unsupervised "
                    + "anomaly score (15 per cent). The anomaly component identifies records that differ "
                    + "from the reference population; it is a prompt for review and is not evidence of "
                    + "wrongdoing. A confirmed exclusion or a malformed identifier sets the recommendation "
                    + "to Reject regardless of the other components.");

            auditService.record(generatedBy, null, "REPORT_EXPORT", "REPORTING",
                    "Risk Analysis Report generated for provider " + providerId, "SUCCESS", null);
            return writer.toByteArray();
        }
    }

    /**
     * Builds the Audit Log Report.
     *
     * @param generatedBy the account requesting the report
     * @return the rendered PDF
     * @throws IOException if the document cannot be written
     */
    public byte[] auditLogReport(String generatedBy) throws IOException {
        List<AuditLog> entries = auditLogRepository.findTop200ByOrderByTimestampDesc();

        try (PdfReportWriter writer = new PdfReportWriter()) {
            writer.title("Audit Log Report",
                    "AI-Enhanced Provider Credentialing Automation Platform  |  Generated "
                            + TIMESTAMP.format(ZonedDateTime.now()) + " by " + generatedBy);

            writer.paragraph("The 200 most recent recorded actions, newest first.");
            writer.section("Entries");

            List<String[]> rows = new ArrayList<>();
            for (AuditLog entry : entries) {
                rows.add(new String[]{
                        TIMESTAMP.format(entry.getTimestamp()),
                        entry.getUsername(),
                        entry.getAction(),
                        entry.getModule(),
                        entry.getOutcome()});
            }
            writer.table(new String[]{"Timestamp", "User", "Action", "Module", "Outcome"},
                    rows, new float[]{150f, 90f, 130f, 90f, 60f});

            auditService.record(generatedBy, null, "REPORT_EXPORT", "REPORTING",
                    "Audit Log Report generated", "SUCCESS", null);
            return writer.toByteArray();
        }
    }

    /**
     * Exports the verification queue as CSV.
     *
     * @param generatedBy the account requesting the export
     * @return CSV bytes, UTF-8 encoded
     */
    public byte[] providerQueueCsv(String generatedBy) {
        List<ProviderSummaryDto> providers = queryService.listProviders(null, null);

        StringBuilder csv = new StringBuilder(
                "Provider ID,NPI,Name,Entity type,Status,NPI status,Exclusion status,"
                        + "Risk score,Recommendation,State,Last verified\n");

        for (ProviderSummaryDto provider : providers) {
            csv.append(quote(provider.id())).append(',')
                    .append(quote(provider.npi())).append(',')
                    .append(quote(provider.displayName())).append(',')
                    .append(quote(provider.npiType())).append(',')
                    .append(quote(provider.status())).append(',')
                    .append(quote(provider.npiStatus())).append(',')
                    .append(quote(provider.exclusionStatus())).append(',')
                    .append(quote(provider.riskScore())).append(',')
                    .append(quote(provider.recommendation())).append(',')
                    .append(quote(provider.state())).append(',')
                    .append(quote(provider.lastVerifiedAt())).append('\n');
        }

        auditService.record(generatedBy, null, "REPORT_EXPORT", "REPORTING",
                "Verification queue exported as CSV (" + providers.size() + " rows)", "SUCCESS", null);
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Renders a value as a quoted, escaped CSV field. */
    private String quote(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        return '"' + text + '"';
    }

    /** Joins the provider's address parts onto one line. */
    private String joinAddress(ProviderDetailDto provider) {
        StringBuilder builder = new StringBuilder();
        if (provider.addressLine1() != null) {
            builder.append(provider.addressLine1());
        }
        if (provider.city() != null) {
            builder.append(builder.length() > 0 ? ", " : "").append(provider.city());
        }
        if (provider.state() != null) {
            builder.append(builder.length() > 0 ? ", " : "").append(provider.state());
        }
        if (provider.postalCode() != null) {
            builder.append(' ').append(provider.postalCode());
        }
        return builder.length() == 0 ? null : builder.toString();
    }
}
