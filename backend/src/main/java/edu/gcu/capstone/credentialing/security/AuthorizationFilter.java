package edu.gcu.capstone.credentialing.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.gcu.capstone.credentialing.domain.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AuthorizationFilter.java
 *
 * <p>Enforces authentication and the role-based access matrix on every API
 * request. The filter runs before any controller, so no endpoint can be reached
 * without passing it, and a new controller cannot be added without inheriting
 * the same protection.</p>
 *
 * <p>The matrix implemented here is the one specified in Milestone 1:</p>
 * <pre>
 *   Capability                         ADMIN  REVIEWER  VIEWER
 *   View dashboards and reports          yes     yes      yes
 *   Upload documents, run verification   yes     yes      no
 *   Record a credentialing decision      yes     yes      no
 *   Administer users and configuration   yes     no       no
 * </pre>
 *
 * <p>Two response codes are used deliberately: 401 when no valid session was
 * presented, and 403 when the session is valid but the role is insufficient.
 * Conflating them would leave a client unable to tell "sign in again" from
 * "you may not do this".</p>
 */
@Component
@Order(1)
public class AuthorizationFilter extends OncePerRequestFilter {

    /** Endpoints reachable without a session. */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/health");

    /** Prefix reserved for administrator-only endpoints. */
    private static final String ADMIN_PREFIX = "/api/admin";

    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthorizationFilter(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only the API is protected. Static assets of the Angular application are
        // public by necessity; every endpoint that returns data is not.
        if (!path.startsWith("/api/") || PUBLIC_PATHS.contains(path)
                || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        Optional<UserSession> session = sessionRegistry.resolve(extractToken(request));
        if (session.isEmpty()) {
            reject(response, HttpStatus.UNAUTHORIZED,
                    "Your session has ended or was not recognised. Please sign in again.");
            return;
        }

        UserSession active = session.get();
        if (!isPermitted(active.getRole(), request.getMethod(), path)) {
            reject(response, HttpStatus.FORBIDDEN,
                    "Your role (" + active.getRole() + ") does not permit this action.");
            return;
        }

        // Controllers read the caller from this attribute rather than re-parsing
        // the token, which keeps identity resolution in one place.
        request.setAttribute(SESSION_ATTRIBUTE, active);
        chain.doFilter(request, response);
    }

    /** Request attribute under which the resolved session is published. */
    public static final String SESSION_ATTRIBUTE = "credentialing.session";

    /**
     * Applies the access matrix.
     *
     * @param role   the caller's role
     * @param method the HTTP method
     * @param path   the request path
     * @return true when the action is permitted
     */
    private boolean isPermitted(Role role, String method, String path) {
        if (path.startsWith(ADMIN_PREFIX)) {
            return role == Role.ADMIN;
        }
        boolean readOnly = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        if (readOnly) {
            return true; // All three roles may read.
        }
        // Signing out is always permitted to an authenticated caller.
        if (path.equals("/api/auth/logout")) {
            return true;
        }
        return role == Role.ADMIN || role == Role.REVIEWER;
    }

    /**
     * Reads the bearer token from the Authorization header, falling back to the
     * {@code X-Session-Token} header used by the Angular client.
     *
     * @param request the inbound request
     * @return the token, or null when none was presented
     */
    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return request.getHeader("X-Session-Token");
    }

    /** Writes a JSON error body with the given status. */
    private void reject(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message));
    }
}
