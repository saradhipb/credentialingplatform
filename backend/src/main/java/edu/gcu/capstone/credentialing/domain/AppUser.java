package edu.gcu.capstone.credentialing.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * AppUser.java
 *
 * <p>An account that can sign in to the platform. Passwords are never stored;
 * the table holds only a per-user random salt and a PBKDF2-HMAC-SHA256 hash
 * produced by {@code PasswordHasher}.</p>
 *
 * <p>In an operational deployment this table would be replaced by the hosting
 * organization's identity provider. It exists in the prototype so that
 * role-based access control can be demonstrated end to end.</p>
 */
@Entity
@Table(name = "app_users", indexes = {
        @Index(name = "idx_user_username", columnList = "username", unique = true)
})
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80, nullable = false, unique = true)
    private String username;

    @Column(length = 120)
    private String displayName;

    @Column(length = 160)
    private String email;

    /** Base64-encoded PBKDF2 derived key. */
    @Column(length = 200, nullable = false)
    private String passwordHash;

    /** Base64-encoded 16-byte random salt, unique per user. */
    @Column(length = 60, nullable = false)
    private String salt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Role role = Role.VIEWER;

    @Column(nullable = false)
    private boolean active = true;

    private Instant lastLoginAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // ----- accessors ------------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
