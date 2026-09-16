package edu.gcu.capstone.credentialing.web;

import edu.gcu.capstone.credentialing.web.dto.DashboardMetricsDto;
import edu.gcu.capstone.credentialing.web.dto.ProviderSummaryDto;
import edu.gcu.capstone.credentialing.workflow.ProviderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * DashboardController.java
 *
 * <p>Aggregate figures for the Verification Dashboard and the review queue.</p>
 *
 * <p>Endpoints: {@code GET /api/dashboard/metrics},
 * {@code GET /api/dashboard/review-queue}.</p>
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ProviderQueryService queryService;

    public DashboardController(ProviderQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Returns the dashboard snapshot.
     *
     * @return counts, averages and the recent-activity table
     */
    @GetMapping("/metrics")
    public DashboardMetricsDto metrics() {
        return queryService.getDashboardMetrics();
    }

    /**
     * Returns assessments awaiting a human decision.
     *
     * @return queue rows, highest risk first
     */
    @GetMapping("/review-queue")
    public List<ProviderSummaryDto> reviewQueue() {
        return queryService.listReviewQueue();
    }
}
