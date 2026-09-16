package edu.gcu.capstone.credentialing.web.dto;

/**
 * LoginResponse.java
 *
 * <p>What the client receives after a successful sign-in. The token is opaque
 * and must accompany every later request.</p>
 *
 * @param token             the session token
 * @param username          the signed-in account name
 * @param displayName       the name shown in the interface
 * @param role              the role granted to this session
 * @param sessionTimeoutMinutes how long the session survives without activity
 */
public record LoginResponse(String token, String username, String displayName,
                            String role, int sessionTimeoutMinutes) {
}
