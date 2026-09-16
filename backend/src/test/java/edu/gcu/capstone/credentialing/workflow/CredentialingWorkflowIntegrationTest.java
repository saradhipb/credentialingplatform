package edu.gcu.capstone.credentialing.workflow;

import edu.gcu.capstone.credentialing.domain.*;
import edu.gcu.capstone.credentialing.security.UserSession;
import edu.gcu.capstone.credentialing.web.dto.DashboardMetricsDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CredentialingWorkflowIntegrationTest.java
 *
 * <p>End-to-end tests of the complete pipeline with the real Spring context:
 * upload, text extraction, field parsing, verification against the bundled
 * datasets, anomaly detection, risk scoring and persistence.</p>
 *
 * <p>Where the module tests confirm each stage behaves correctly in isolation,
 * these tests confirm the stages are wired together in the right order and that
 * the workflow state a reviewer sees follows from the evidence gathered.</p>
 */
@SpringBootTest
class CredentialingWorkflowIntegrationTest {

    @Autowired
    private CredentialingWorkflowService workflowService;

    @Autowired
    private ProviderQueryService queryService;

    /** A signed-in reviewer, as the authorization filter would supply. */
    private static final UserSession REVIEWER =
            new UserSession("test-token", 1L, "reviewer", "Test Reviewer", Role.REVIEWER);

    @Test
    @Transactional
    @DisplayName("A clean provider is processed to Active with an Approve recommendation")
    void processesCleanProvider() {
        var result = workflowService.process(document("clean.txt", """
                ARIZONA MEDICAL BOARD
                PRIMARY SOURCE VERIFICATION LETTER

                Provider Name: Harper, Dana R., M.D.
                Entity Type: 1
                NPI Number: 1746805930
                License Number: A12345
                License State: Arizona
                Practice Address: 4410 N Central Ave Ste 210
                Phoenix, AZ 85012
                """), "State license", REVIEWER, "127.0.0.1");

        assertThat(result.provider().getNpi()).isEqualTo("1746805930");
        assertThat(result.provider().getLastName()).isEqualTo("Harper");
        assertThat(result.provider().getStatus()).isEqualTo(ProviderStatus.ACTIVE);
        assertThat(result.verification().getExclusionStatus()).isEqualTo(ExclusionStatus.NOT_EXCLUDED);
        assertThat(result.assessment().getRecommendation()).isEqualTo(RiskRecommendation.APPROVE);
        assertThat(result.document().getStatus()).isEqualTo(DocumentStatus.PARSED);
    }

    @Test
    @Transactional
    @DisplayName("An excluded provider is processed to Rejected")
    void processesExcludedProvider() {
        var result = workflowService.process(document("excluded.txt", """
                Provider Name: Whitfield, Reginald T., M.D.
                Entity Type: 1
                NPI: 1458293762
                License Number: D55219
                Practice Address: 2810 E Speedway Blvd
                Tucson, AZ 85716
                """), "Credential summary", REVIEWER, "127.0.0.1");

        assertThat(result.verification().getExclusionStatus()).isEqualTo(ExclusionStatus.EXCLUDED);
        assertThat(result.assessment().getRecommendation()).isEqualTo(RiskRecommendation.REJECT);
        assertThat(result.provider().getStatus()).isEqualTo(ProviderStatus.REJECTED);
        // The exclusion component alone contributes forty of the hundred points,
        // so the score clears 40. It deliberately does NOT reach the 70-point
        // reject threshold: the rejection comes from override rule 1, not from
        // the arithmetic. Asserting both bounds is what pins that behaviour down.
        assertThat(result.assessment().getScore()).isGreaterThan(40.0);
        assertThat(result.assessment().getScore()).isLessThan(70.0);
    }

    @Test
    @Transactional
    @DisplayName("A document nothing can be read from is routed to manual review")
    void routesUnreadableDocumentToManualReview() {
        var result = workflowService.process(document("blank.txt", "   \n  \n"),
                "Unknown", REVIEWER, "127.0.0.1");

        assertThat(result.provider().getStatus()).isEqualTo(ProviderStatus.MANUAL_REVIEW);
        assertThat(result.verification()).isNull();
        assertThat(result.assessment()).isNull();
        assertThat(result.warning()).isNotBlank();
    }

    @Test
    @Transactional
    @DisplayName("A document that disagrees with the registry is flagged for review")
    void flagsDiscrepancies() {
        var result = workflowService.process(document("mismatch.txt", """
                Provider Name: Okafor, Chidi, M.D.
                Entity Type: 1
                National Provider Identifier: 1283947566
                License Number: B77120
                Practice Address: 4455 E Camelback Rd Suite 300
                Scottsdale, AZ 85018
                """), "Application attachment", REVIEWER, "127.0.0.1");

        assertThat(result.verification().getDiscrepancyCount()).isGreaterThan(0);
        assertThat(result.assessment().getFlags()).contains("differ from the registry record");
    }

    @Test
    @Transactional
    @DisplayName("Two documents for the same identifier attach to one provider record")
    void reusesProviderRecordForSameIdentifier() {
        var first = workflowService.process(document("a.txt", """
                Provider Name: Nakamura, Kenji, M.D.
                NPI Number: 1637482955
                Practice Address: 3420 S Val Vista Dr
                Gilbert, AZ 85297
                """), "State license", REVIEWER, "127.0.0.1");

        var second = workflowService.process(document("b.txt", """
                Provider Name: Nakamura, Kenji, M.D.
                NPI Number: 1637482955
                License Number: E91336
                Practice Address: 3420 S Val Vista Dr
                Gilbert, AZ 85297
                """), "Board certification", REVIEWER, "127.0.0.1");

        assertThat(second.provider().getId()).isEqualTo(first.provider().getId());
    }

    @Test
    @Transactional
    @DisplayName("Processed providers appear in the dashboard totals")
    void updatesDashboardMetrics() {
        workflowService.process(document("metrics.txt", """
                Provider Name: Osei-Bonsu, Ama, M.D.
                NPI Number: 1320594876
                License Number: F20477
                Practice Address: 2400 S Avenue A
                Yuma, AZ 85364
                """), "State license", REVIEWER, "127.0.0.1");

        DashboardMetricsDto metrics = queryService.getDashboardMetrics();

        assertThat(metrics.totalProviders()).isGreaterThan(0);
        assertThat(metrics.statusBreakdown()).containsKey(ProviderStatus.ACTIVE.name());
        assertThat(metrics.recentProviders()).isNotEmpty();
    }

    /** Builds a multipart upload from inline text. */
    private MockMultipartFile document(String filename, String content) {
        return new MockMultipartFile("file", filename, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
