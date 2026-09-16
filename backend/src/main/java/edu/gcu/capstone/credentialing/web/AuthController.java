package edu.gcu.capstone.credentialing.web;

import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import edu.gcu.capstone.credentialing.security.AuthenticationService;
import edu.gcu.capstone.credentialing.security.AuthorizationFilter;
import edu.gcu.capstone.credentialing.security.UserSession;
import edu.gcu.capstone.credentialing.web.dto.LoginRequest;
import edu.gcu.capstone.credentialing.web.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AuthController.java
 *
 * <p>Sign-in and sign-out endpoints, and a lookup that lets a returning client
 * confirm its stored token is still valid before rendering a protected screen.</p>
 *
 * <p>Endpoints: {@code POST /api/auth/login}, {@code POST /api/auth/logout},
 * {@code GET /api/auth/me}.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final ApplicationProperties properties;

    public AuthController(AuthenticationService authenticationService,
                          ApplicationProperties properties) {
        this.authenticationService = authenticationService;
        this.properties = properties;
    }

    /**
     * Authenticates a user and opens a session.
     *
     * @param loginRequest the submitted credentials
     * @param request      the inbound request, used for the audit trail
     * @return the session token and the caller's role
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest loginRequest,
                               HttpServletRequest request) {
        UserSession session = authenticationService.authenticate(
                loginRequest.username(), loginRequest.password(),
                ControllerSupport.clientAddress(request));

        return new LoginResponse(session.getToken(), session.getUsername(),
                session.getDisplayName(), session.getRole().name(),
                properties.getSecurity().getSessionTimeoutMinutes());
    }

    /**
     * Ends the caller's session.
     *
     * @param request the inbound request
     * @return an empty 204 response
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Object attribute = request.getAttribute(AuthorizationFilter.SESSION_ATTRIBUTE);
        if (attribute instanceof UserSession session) {
            authenticationService.signOut(session, ControllerSupport.clientAddress(request));
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Describes the current session, used by the client on start-up.
     *
     * @param request the inbound request
     * @return the caller's username, display name and role
     */
    @GetMapping("/me")
    public Map<String, Object> currentUser(HttpServletRequest request) {
        UserSession session = ControllerSupport.currentSession(request);
        return Map.of(
                "username", session.getUsername(),
                "displayName", session.getDisplayName() == null ? session.getUsername() : session.getDisplayName(),
                "role", session.getRole().name());
    }
}
