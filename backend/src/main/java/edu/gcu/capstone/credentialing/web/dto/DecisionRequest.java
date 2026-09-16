package edu.gcu.capstone.credentialing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DecisionRequest.java
 *
 * <p>A credentialing decision recorded by a human reviewer. The decision is
 * stored alongside the platform's recommendation rather than replacing it, so
 * the audit trail preserves both what was advised and what was decided.</p>
 *
 * @param decision APPROVED, ESCALATED or DENIED
 * @param note     the reviewer's justification
 */
public record DecisionRequest(
        @NotBlank(message = "Choose a decision")
        @Pattern(regexp = "APPROVED|ESCALATED|DENIED",
                message = "Decision must be APPROVED, ESCALATED or DENIED")
        String decision,
        String note) {
}
