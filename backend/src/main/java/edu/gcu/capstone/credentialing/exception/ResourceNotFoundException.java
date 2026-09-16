package edu.gcu.capstone.credentialing.exception;

/**
 * ResourceNotFoundException.java
 *
 * <p>Raised when a requested provider, document, verification or assessment does
 * not exist. Mapped to HTTP 404 by {@code GlobalExceptionHandler}.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * @param type the kind of record sought, e.g. "Provider"
     * @param id   the identifier that was not found
     */
    public ResourceNotFoundException(String type, Object id) {
        super(type + " " + id + " was not found.");
    }
}
