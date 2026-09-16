package edu.gcu.capstone.credentialing.web;

import edu.gcu.capstone.credentialing.security.AuthorizationFilter;
import edu.gcu.capstone.credentialing.security.UserSession;
import jakarta.servlet.http.HttpServletRequest;

/**
 * ControllerSupport.java
 *
 * <p>Small helpers shared by the REST controllers: retrieving the authenticated
 * caller that {@code AuthorizationFilter} attached to the request, and
 * determining the client address for the audit trail.</p>
 */
public final class ControllerSupport {

    private ControllerSupport() {
    }

    /**
     * Returns the session the authorization filter resolved for this request.
     *
     * @param request the inbound request
     * @return the authenticated session, never null for a protected endpoint
     * @throws IllegalStateException if called on an unprotected endpoint
     */
    public static UserSession currentSession(HttpServletRequest request) {
        Object session = request.getAttribute(AuthorizationFilter.SESSION_ATTRIBUTE);
        if (session instanceof UserSession userSession) {
            return userSession;
        }
        throw new IllegalStateException("No authenticated session is attached to this request");
    }

    /**
     * Determines the caller's address, honouring a reverse-proxy header when one
     * is present so that a deployment behind a load balancer still records the
     * originating client rather than the proxy.
     *
     * @param request the inbound request
     * @return the client address
     */
    public static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
