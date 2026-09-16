package edu.gcu.capstone.credentialing.ocr;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * PlainTextEngine.java
 *
 * <p>Reads {@code .txt} and {@code .csv} submissions directly. Some licensing
 * boards supply verification letters as plain text exports, and the sample
 * documents bundled with the prototype use this format so that the full
 * pipeline can be demonstrated on a machine without Tesseract installed.</p>
 */
@Component
@Order(0)
public class PlainTextEngine implements OcrEngine {

    @Override
    public boolean supports(Path file, String contentType) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".txt") || name.endsWith(".csv")
                || (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/"));
    }

    @Override
    public OcrResult extract(Path file) {
        long start = System.currentTimeMillis();
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).strip();
            long elapsed = System.currentTimeMillis() - start;
            return text.isBlank()
                    ? OcrResult.empty("The uploaded text file was empty.")
                    : new OcrResult(text, OcrResult.ENGINE_PLAIN_TEXT, 1.0, elapsed, null);
        } catch (Exception ex) {
            return OcrResult.empty("Text file could not be read: " + ex.getClass().getSimpleName());
        }
    }

    @Override
    public String name() {
        return OcrResult.ENGINE_PLAIN_TEXT;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
