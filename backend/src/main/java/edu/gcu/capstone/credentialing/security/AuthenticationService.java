package edu.gcu.capstone.credentialing.security;

import edu.gcu.capstone.credentialing.audit.AuditService;
import edu.gcu.capstone.credentialing.domain.AppUser;
import edu.gcu.capstone.credentialing.domain.Role;
import edu.gcu.capstone.credentialing.repository.AppUserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * AuthenticationService.java
 *
 * <p>Verifies credentials and issues sessions. Every attempt, successful or not,
 * is written to the audit trail, because a record of failed sign-ins is as much
 * a compliance artefact as a record of successful ones.</p>
 *
 * <p>Failures return the same message whatever went wrong. Telling a caller that
 * a username exists but the password was wrong would let an attacker enumerate
 * accounts, so the distinction is kept in the audit log and out of the
 * response.</p>
 */
@Service
public class AuthenticationService {

    private static final String GENERIC_FAILURE =
            "The username or password is incorrect.";

    private final AppUserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final SessionRegistry sessionRegistry;
    private final AuditService auditService;

    public AuthenticationService(AppUserRepository userRepository,
                                 PasswordHasher passwordHasher,
                                 SessionRegistry sessionRegistry,
                                 AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.sessionRegistry = sessionRegistry;
        this.auditService = auditService;
    }

    /**
     * Authenticates a user and opens a session.
     *
     * @param username  the supplied username
     * @param password  the supplied password
     * @param ipAddress the caller's address, recorded in the audit trail
     * @return the new session
     * @throws AuthenticationFailedException when the credentials are not valid
     */
    public UserSession authenticate(String username, String password, String ipAddress) {
        Optional<AppUser> found = userRepository.findByUsernameIgnoreCase(
                username == null ? "" : username.trim());

        if (found.isEmpty()) {
            auditService.record(username, null, "LOGIN_FAILURE", "SECURITY",
                    "Sign-in attempted for an unknown username", "FAILURE", ipAddress);
            throw new AuthenticationFailedException(GENERIC_FAILURE);
        }

        AppUser user = found.get();
        if (!user.isActive()) {
            auditService.record(user.getUsername(), user.getRole().name(), "LOGIN_FAILURE", "SECURITY",
                    "Sign-in attempted on a disabled account", "FAILURE", ipAddress);
            throw new AuthenticationFailedException("This account has been disabled.");
        }

        char[] supplied = password == null ? new char[0] : password.toCharArray();
        boolean correct = passwordHasher.matches(supplied, user.getSalt(), user.getPasswordHash());
        java.util.Arrays.fill(supplied, '\0'); // Clear the copy promptly.

        if (!correct) {
            auditService.record(user.getUsername(), user.getRole().name(), "LOGIN_FAILURE", "SECURITY",
                    "Incorrect password supplied", "FAILURE", ipAddress);
            throw new AuthenticationFailedException(GENERIC_FAILURE);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        UserSession session = sessionRegistry.create(user);
        auditService.record(user.getUsername(), user.getRole().name(), "LOGIN_SUCCESS", "SECURITY",
                "Session opened", "SUCCESS", ipAddress);
        return session;
    }

    /**
     * Ends a session.
     *
     * @param session   the session to close, may be null
     * @param ipAddress the caller's address
     */
    public void signOut(UserSession session, String ipAddress) {
        if (session == null) {
            return;
        }
        sessionRegistry.invalidate(session.getToken());
        auditService.record(session.getUsername(), session.getRole().name(), "LOGOUT", "SECURITY",
                "Session closed", "SUCCESS", ipAddress);
    }

    /**
     * Creates an account. Used by the data seeder and by the administrator's
     * user-management screen.
     *
     * @param username    unique sign-in name
     * @param displayName name shown in the interface
     * @param email       contact address
     * @param password    initial password, hashed immediately and not retained
     * @param role        the role to assign
     * @return the saved account
     */
    public AppUser createUser(String username, String displayName, String email,
                              String password, Role role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setRole(role);

        String salt = passwordHasher.newSalt();
        user.setSalt(salt);
        user.setPasswordHash(passwordHasher.hash(password.toCharArray(), salt));
        return userRepository.save(user);
    }

    /** Raised when supplied credentials are not valid. */
    public static class AuthenticationFailedException extends RuntimeException {
        public AuthenticationFailedException(String message) {
            super(message);
        }
    }
}
