package edu.gcu.capstone.credentialing.exception;

import edu.gcu.capstone.credentialing.security.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler.java
 *
 * <p>Converts exceptions into consistent JSON error responses so that the
 * Angular client can present one error component for every failure.</p>
 *
 * <p>Expected failures return a message written for the person reading it.
 * Unexpected failures return a generic message and log the detail on the server,
 * because a stack trace or SQL fragment in a browser response is an information
 * disclosure, not a diagnostic aid.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 400 - the request was rejected because of what the user supplied. */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** 400 - a request body failed Bean Validation. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleBinding(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("The information supplied was not valid.");
        return build(HttpStatus.BAD_REQUEST, message);
    }

    /** 401 - the supplied credentials were not accepted. */
    @ExceptionHandler(AuthenticationService.AuthenticationFailedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(
            AuthenticationService.AuthenticationFailedException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /** 404 - the requested record does not exist. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** 413 - the upload exceeded the configured size limit. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "That file is larger than the 15 MB limit. Split the document or reduce the scan resolution.");
    }

    /** 500 - anything not anticipated above. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled error while processing a request", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "The platform could not complete that action. The error has been logged. "
                        + "If it continues, contact your system administrator.");
    }

    /** Builds the standard error body. */
    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
