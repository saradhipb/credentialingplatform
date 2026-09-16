package edu.gcu.capstone.credentialing.ocr;

/**
 * OcrResult.java
 *
 * <p>Immutable value object returned by every text-extraction engine. Carrying
 * the engine name, a confidence estimate and the elapsed time alongside the
 * text lets the platform record <em>how</em> a piece of text was obtained, which
 * the dashboard displays and the audit trail preserves.</p>
 *
 * @param text       the extracted text, never null (empty when nothing was read)
 * @param engine     identifier of the engine that produced the text
 * @param confidence heuristic confidence in the range 0.0-1.0
 * @param millis     wall-clock duration of the extraction
 * @param note       short explanation of fallbacks or failures, may be null
 */
public record OcrResult(String text, String engine, double confidence, long millis, String note) {

    /** Engine identifier used when text is read from a digital PDF's text layer. */
    public static final String ENGINE_PDF_TEXT_LAYER = "PDF_TEXT_LAYER";

    /** Engine identifier used when text is produced by the Tesseract OCR engine. */
    public static final String ENGINE_TESSERACT = "TESSERACT_OCR";

    /** Engine identifier used when a plain-text file is read directly. */
    public static final String ENGINE_PLAIN_TEXT = "PLAIN_TEXT";

    /** Engine identifier recorded when no engine was able to read the document. */
    public static final String ENGINE_NONE = "NONE";

    /**
     * Builds the result used when extraction produced nothing usable.
     *
     * @param note explanation to show the reviewer
     * @return an empty result with zero confidence
     */
    public static OcrResult empty(String note) {
        return new OcrResult("", ENGINE_NONE, 0.0, 0L, note);
    }

    /** @return true when the engine produced at least some usable text */
    public boolean hasText() {
        return text != null && !text.isBlank();
    }
}
