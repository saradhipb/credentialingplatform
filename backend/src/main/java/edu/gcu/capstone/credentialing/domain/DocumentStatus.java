package edu.gcu.capstone.credentialing.domain;

/**
 * DocumentStatus.java
 *
 * <p>Processing state of an uploaded credential document as it moves through
 * the intake pipeline: stored, text extracted, fields parsed, or failed.</p>
 */
public enum DocumentStatus {
    /** File accepted and written to the upload directory. */
    UPLOADED,
    /** Text extraction succeeded (OCR or PDF text layer). */
    EXTRACTED,
    /** Provider fields were parsed out of the extracted text. */
    PARSED,
    /** Text extraction produced no usable content; routed to manual review. */
    EXTRACTION_FAILED,
    /** The stored binary has been purged under the retention policy. */
    PURGED
}
