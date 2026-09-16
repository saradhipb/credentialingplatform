package edu.gcu.capstone.credentialing;

import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * CredentialingPlatformApplication.java
 *
 * <p>Entry point of the AI-Enhanced Provider Credentialing Automation Platform.
 * Starting this class boots the embedded web server, creates the H2 schema from
 * the JPA entities, loads the OIG exclusion list and the NPPES offline snapshot,
 * trains the anomaly-detection model, and serves both the REST API and the
 * compiled Angular single-page application.</p>
 *
 * <p>Run with {@code mvn spring-boot:run} or
 * {@code java -jar target/credentialing-platform.jar}.</p>
 *
 * @author saradhi pb
 * @version 1.0.0
 */
@SpringBootApplication
@EnableConfigurationProperties(ApplicationProperties.class)
public class CredentialingPlatformApplication {

    /**
     * Standard Java entry point.
     *
     * @param args command-line arguments forwarded to Spring Boot, which allows
     *             any property in application.yml to be overridden at launch.
     */
    public static void main(String[] args) {
        SpringApplication.run(CredentialingPlatformApplication.class, args);
    }
}
