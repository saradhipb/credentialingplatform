package edu.gcu.capstone.credentialing.security;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PasswordHasher.java
 *
 * <p>Derives and verifies password hashes using PBKDF2 with HMAC-SHA256.</p>
 *
 * <p>Three properties matter here. A password is never stored, only a derived
 * key. Each account carries its own 16-byte random salt, so identical passwords
 * produce different hashes and a precomputed table is useless. And the
 * derivation runs 210,000 iterations, the figure OWASP publishes for
 * PBKDF2-HMAC-SHA256, which makes an offline guessing attack expensive.</p>
 *
 * <p>Verification uses a constant-time comparison so that the time taken to
 * reject a password does not reveal how much of it was correct.</p>
 */
@Component
public class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a fresh random salt.
     *
     * @return the salt, Base64 encoded for storage
     */
    public String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Derives the stored hash for a password and salt.
     *
     * @param password   the plain-text password, never persisted
     * @param base64Salt the account's salt as returned by {@link #newSalt()}
     * @return the derived key, Base64 encoded
     * @throws IllegalStateException when the JVM lacks the required algorithm
     */
    public String hash(char[] password, String base64Salt) {
        try {
            byte[] salt = Base64.getDecoder().decode(base64Salt);
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] derived = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return Base64.getEncoder().encodeToString(derived);
        } catch (Exception ex) {
            throw new IllegalStateException("Password hashing is unavailable on this JVM", ex);
        }
    }

    /**
     * Verifies a supplied password against a stored hash.
     *
     * @param password     the password entered at sign-in
     * @param base64Salt   the account's stored salt
     * @param expectedHash the account's stored hash
     * @return true when the password is correct
     */
    public boolean matches(char[] password, String base64Salt, String expectedHash) {
        String actual = hash(password, base64Salt);
        // Constant-time comparison: a length or prefix difference must not be
        // observable from how quickly the answer comes back.
        return MessageDigest.isEqual(
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expectedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
