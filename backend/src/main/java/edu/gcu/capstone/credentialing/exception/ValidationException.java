package edu.gcu.capstone.credentialing.exception;

/**
 * ValidationException.java
 *
 * <p>Raised when a request is rejected because of what the user supplied, such
 * as an unsupported file type or a missing field. The message is written to be
 * shown directly to a non-technical user, so it says what to do rather than what
 * failed internally.</p>
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
