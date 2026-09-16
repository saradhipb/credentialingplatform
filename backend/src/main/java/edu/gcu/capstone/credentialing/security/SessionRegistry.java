package edu.gcu.capstone.credentialing.security;

import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import edu.gcu.capstone.credentialing.domain.AppUser;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionRegistry.java
 *
 * <p>Server-side store of active sessions, keyed by an opaque token issued at
 * sign-in. Holding session state on the server means a session can be revoked
 * immediately, which a self-contained client-side token cannot offer without
 * additional machinery.</p>
 *
 * <p>Tokens are 256 bits of {@link SecureRandom} output, Base64url encoded.</p>
 */
@Component
public class SessionRegistry {

    private static final int TOKEN_BYTES = 32;

    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final ApplicationProperties properties;

    public SessionRegistry(ApplicationProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates a session for a successfully authenticated user.
     *
     * @param user the authenticated account
     * @return the new session, including the token to return to the client
     */
    public UserSession create(AppUser user) {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        UserSession session = new UserSession(token, user.getId(), user.getUsername(),
                user.getDisplayName(), user.getRole());
        sessions.put(token, session);
        return session;
    }

    /**
     * Resolves a token to an active session, removing it if it has timed out.
     *
     * @param token the token presented by the client, may be null
     * @return the live session, or empty when the token is unknown or expired
     */
    public Optional<UserSession> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        UserSession session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired(properties.getSecurity().getSessionTimeoutMinutes())) {
            sessions.remove(token);
            return Optional.empty();
        }
        session.touch();
        return Optional.of(session);
    }

    /**
     * Ends a session.
     *
     * @param token the token to revoke
     */
    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** @return every currently active session, for the administrator's panel */
    public Collection<UserSession> activeSessions() {
        sessions.values().removeIf(s -> s.isExpired(properties.getSecurity().getSessionTimeoutMinutes()));
        return sessions.values();
    }
}
