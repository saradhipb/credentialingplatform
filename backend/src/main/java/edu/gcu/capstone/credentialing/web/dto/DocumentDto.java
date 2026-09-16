package edu.gcu.capstone.credentialing.web.dto;

/**
 * DocumentDto.java
 *
 * <p>Metadata about one uploaded document. The extracted text is returned only
 * as a short preview, because the full text is not needed to review a decision
 * and returning it everywhere would spread document content across the client
 * for no benefit.</p>
 *
 * @param id            document identifier
 * @param filename      the name the user uploaded
 * @param documentType  classification chosen at upload
 * @param status        processing status
 * @param engine        which engine read the document
 * @param confidence    extraction confidence, 0.0-1.0
 * @param sizeBytes     stored size
 * @param uploadedBy    the account that uploaded it
 * @param uploadedAt    ISO-8601 upload timestamp
 * @param textPreview   first 400 characters of the extracted text
 * @param purged        true when the stored binary has been deleted
 */
public record DocumentDto(Long id, String filename, String documentType, String status,
                          String engine, double confidence, long sizeBytes,
                          String uploadedBy, String uploadedAt, String textPreview,
                          boolean purged) {
}
