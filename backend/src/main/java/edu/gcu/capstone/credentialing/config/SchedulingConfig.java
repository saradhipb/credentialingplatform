package edu.gcu.capstone.credentialing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SchedulingConfig.java
 *
 * <p>Enables Spring's scheduler, which drives the nightly document retention
 * purge in {@code DocumentRetentionService}. Scheduling is switched on in its own
 * configuration class so that a test can exclude it and avoid background work
 * running during a test method.</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
