package edu.gcu.capstone.credentialing.web.dto;

/**
 * UserDto.java
 *
 * <p>An account as shown on the administrator's user-management screen. No
 * password material appears here: the hash and salt never leave the persistence
 * layer.</p>
 *
 * @param id          account identifier
 * @param username    sign-in name
 * @param displayName name shown in the interface
 * @param email       contact address
 * @param role        assigned role
 * @param active      whether the account may sign in
 * @param lastLoginAt ISO-8601 time of the most recent sign-in, or null
 */
public record UserDto(Long id, String username, String displayName, String email,
                      String role, boolean active, String lastLoginAt) {
}
