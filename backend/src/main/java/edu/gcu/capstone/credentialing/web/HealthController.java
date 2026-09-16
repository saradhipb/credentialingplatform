package edu.gcu.capstone.credentialing.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * HealthController.java
 *
 * <p>Unauthenticated liveness endpoint used by deployment tooling to confirm the
 * service has started. It reports nothing beyond liveness, so exposing it
 * without a session discloses nothing of value.</p>
 *
 * <p>Endpoint: {@code GET /api/health}.</p>
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * Reports that the service is running.
     *
     * @return a status word and the current server time
     */
    @GetMapping
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "application", "AI-Enhanced Provider Credentialing Automation Platform",
                "time", Instant.now().toString());
    }
}
