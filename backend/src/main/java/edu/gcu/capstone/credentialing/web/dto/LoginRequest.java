package edu.gcu.capstone.credentialing.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * LoginRequest.java
 *
 * <p>Credentials submitted by the sign-in screen. Bean Validation rejects an
 * empty submission before it reaches the authentication service.</p>
 *
 * @param username the account name
 * @param password the password, never logged or persisted
 */
public record LoginRequest(
        @NotBlank(message = "Enter your username") String username,
        @NotBlank(message = "Enter your password") String password) {
}
