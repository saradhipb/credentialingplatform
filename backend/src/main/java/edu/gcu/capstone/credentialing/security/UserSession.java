package edu.gcu.capstone.credentialing.security;

import edu.gcu.capstone.credentialing.domain.Role;

import java.time.Instant;

/**
 * UserSession.java
 *
 * <p>An authenticated session. The session token is an opaque random value; it
 * carries no user data, so it cannot be decoded or altered by a client the way a
 * self-contained token could be.</p>
 */
public class UserSession {

    private final String token;
    private final Long userId;
    private final String username;
    private final String displayName;
    private final Role role;
    private final Instant createdAt;
    private volatile Instant lastAccessedAt;

    public UserSession(String token, Long userId, String username, String displayName, Role role) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
    }

    /** Records activity, which restarts the idle timeout. */
    public void touch() {
        this.lastAccessedAt = Instant.now();
    }

    /**
     * @param timeoutMinutes configured idle timeout
     * @return true when the session has been idle longer than the timeout
     */
    public boolean isExpired(int timeoutMinutes) {
        return lastAccessedAt.plusSeconds(timeoutMinutes * 60L).isBefore(Instant.now());
    }

    public String getToken() { return token; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
}
