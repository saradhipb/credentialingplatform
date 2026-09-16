package edu.gcu.capstone.credentialing.config;

import edu.gcu.capstone.credentialing.domain.Role;
import edu.gcu.capstone.credentialing.repository.AppUserRepository;
import edu.gcu.capstone.credentialing.security.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * DemoDataSeeder.java
 *
 * <p>Creates the three demonstration accounts, one per role, the first time the
 * platform starts against an empty database. Seeding is controlled by
 * {@code app.security.seed-demo-users}, which must be set to false in any
 * deployment that is not a demonstration.</p>
 *
 * <p>The initial passwords are printed once to the application log so the first
 * user can sign in. They are hashed before storage like any other password and
 * are intended to be changed immediately.</p>
 */
@Component
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final ApplicationProperties properties;
    private final AppUserRepository userRepository;
    private final AuthenticationService authenticationService;

    public DemoDataSeeder(ApplicationProperties properties,
                          AppUserRepository userRepository,
                          AuthenticationService authenticationService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.authenticationService = authenticationService;
    }

    @Override
    public void run(String... args) {
        if (!properties.getSecurity().isSeedDemoUsers()) {
            return;
        }
        if (userRepository.count() > 0) {
            return; // Accounts already exist; never overwrite them.
        }

        authenticationService.createUser("admin", "System Administrator",
                "admin@example.edu", "Admin#2026", Role.ADMIN);
        authenticationService.createUser("reviewer", "Credentialing Specialist",
                "reviewer@example.edu", "Review#2026", Role.REVIEWER);
        authenticationService.createUser("viewer", "Compliance Officer",
                "viewer@example.edu", "Viewer#2026", Role.VIEWER);

        log.info("Created demonstration accounts. Sign in with admin/Admin#2026, "
                + "reviewer/Review#2026 or viewer/Viewer#2026, then change these passwords.");
    }
}
