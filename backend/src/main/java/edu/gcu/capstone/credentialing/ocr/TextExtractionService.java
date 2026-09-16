package edu.gcu.capstone.credentialing.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * TextExtractionService.java
 *
 * <p>Chooses and runs the text-extraction engine for an uploaded document. Spring
 * injects every {@link OcrEngine} implementation in {@code @Order} sequence, so
 * the service tries the cheapest accurate option first (plain text, then the PDF
 * text layer, then optical recognition) and stops at the first engine that
 * returns usable text.</p>
 *
 * <p>This satisfies functional requirement FR-02 (extract data using OCR) while
 * implementing the failure path required by the Milestone 2 architecture: a
 * document that no engine can read is reported as unreadable rather than
 * silently producing empty fields.</p>
 */
@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    private final List<OcrEngine> engines;

    /**
     * @param engines all engines discovered by Spring, already sorted by {@code @Order}
     */
    public TextExtractionService(List<OcrEngine> engines) {
        this.engines = engines;
    }

    /**
     * Extracts text from a stored document.
     *
     * @param file        path to the stored document
     * @param contentType MIME type reported at upload, may be null
     * @return the first usable result, or an empty result describing why none was obtained
     */
    public OcrResult extract(Path file, String contentType) {
        StringBuilder attempts = new StringBuilder();

        for (OcrEngine engine : engines) {
            if (!engine.supports(file, contentType)) {
                continue;
            }
            if (!engine.isAvailable()) {
                attempts.append(engine.name()).append(": unavailable. ");
                continue;
            }

            OcrResult result = engine.extract(file);
            if (result.hasText()) {
                log.info("Extracted {} characters from {} using {} in {} ms",
                        result.text().length(), file.getFileName(), result.engine(), result.millis());
                return result;
            }
            attempts.append(engine.name()).append(": ")
                    .append(result.note() == null ? "no text" : result.note()).append(" ");
        }

        String summary = attempts.length() == 0
                ? "No extraction engine supports this file type."
                : attempts.toString().strip();
        log.warn("Text extraction produced nothing for {} ({})", file.getFileName(), summary);
        return OcrResult.empty(summary);
    }

    /**
     * Reports which engines are usable on this machine, shown on the
     * administrator's integration-health panel.
     *
     * @return one description per engine, e.g. "TESSERACT_OCR: available"
     */
    public List<String> engineStatus() {
        return engines.stream()
                .map(engine -> engine.name() + ": " + (engine.isAvailable() ? "available" : "unavailable"))
                .toList();
    }
}
