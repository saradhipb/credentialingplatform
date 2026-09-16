package edu.gcu.capstone.credentialing.ocr;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * PdfTextLayerEngine.java
 *
 * <p>Reads the embedded text layer of a digitally generated PDF using Apache
 * PDFBox. Most credentialing documents that originate from a licensing board
 * portal are digital PDFs, and for those this engine is both faster and
 * materially more accurate than optical recognition, so it is tried first.</p>
 *
 * <p>A scanned PDF contains page images and no text layer. In that case the
 * extraction returns little or no text and {@code TextExtractionService} falls
 * through to {@link TesseractOcrEngine}.</p>
 */
@Component
@Order(1)
public class PdfTextLayerEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(PdfTextLayerEngine.class);

    /** Below this many characters the PDF is assumed to be a scan, not digital text. */
    private static final int MIN_USEFUL_CHARACTERS = 40;

    @Override
    public boolean supports(Path file, String contentType) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".pdf") || "application/pdf".equalsIgnoreCase(contentType);
    }

    @Override
    public OcrResult extract(Path file) {
        long start = System.currentTimeMillis();
        try (PDDocument document = PDDocument.load(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            long elapsed = System.currentTimeMillis() - start;

            if (text == null || text.strip().length() < MIN_USEFUL_CHARACTERS) {
                return new OcrResult("", ENGINE_UNUSABLE_TEXT, 0.0, elapsed,
                        "PDF has no usable text layer; optical recognition required.");
            }
            // A digital text layer is exact, so confidence is high. It is not set
            // to 1.0 because layout reconstruction can still interleave columns.
            return new OcrResult(text.strip(), OcrResult.ENGINE_PDF_TEXT_LAYER, 0.97, elapsed, null);

        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("PDF text-layer extraction failed for {}: {}", file.getFileName(), ex.toString());
            return new OcrResult("", ENGINE_UNUSABLE_TEXT, 0.0, elapsed,
                    "PDF could not be parsed: " + ex.getClass().getSimpleName());
        }
    }

    /** Marker returned when this engine ran but produced nothing worth keeping. */
    private static final String ENGINE_UNUSABLE_TEXT = "PDF_TEXT_LAYER_EMPTY";

    @Override
    public String name() {
        return OcrResult.ENGINE_PDF_TEXT_LAYER;
    }

    @Override
    public boolean isAvailable() {
        return true; // PDFBox is a pure-Java dependency and always present.
    }
}
