package edu.gcu.capstone.credentialing.ocr;

import java.nio.file.Path;

/**
 * OcrEngine.java
 *
 * <p>Contract implemented by every text-extraction strategy. Defining the
 * capability as an interface is what allows the platform to degrade gracefully:
 * {@code TextExtractionService} asks each engine whether it supports a file and
 * uses the first one that does, so a machine without Tesseract installed still
 * processes digital PDFs instead of failing outright.</p>
 */
public interface OcrEngine {

    /**
     * Reports whether this engine can read the given file.
     *
     * @param file        path to the stored document
     * @param contentType MIME type reported by the browser, may be null
     * @return true when {@link #extract(Path)} should be attempted
     */
    boolean supports(Path file, String contentType);

    /**
     * Extracts text from the document.
     *
     * @param file path to the stored document
     * @return the extracted text with provenance; never null
     */
    OcrResult extract(Path file);

    /** @return a stable identifier for this engine, recorded on each document */
    String name();

    /**
     * Reports whether the engine's runtime prerequisites are present. An engine
     * that is unavailable is skipped rather than allowed to throw.
     *
     * @return true when the engine can run on this machine
     */
    boolean isAvailable();
}
