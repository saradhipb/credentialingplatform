package edu.gcu.capstone.credentialing.ocr;

import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * TesseractOcrEngine.java
 *
 * <p>Optical character recognition for scanned documents and image files. The
 * engine invokes the Tesseract executable as an external process rather than
 * binding to its native library through JNI. That choice keeps the build free
 * of platform-specific native artifacts, so the same JAR runs on Windows,
 * macOS and Linux provided Tesseract itself is installed and on the PATH.</p>
 *
 * <p>Scanned PDFs are handled by rendering each page to a 300 DPI greyscale
 * image with PDFBox and recognising the images in turn; 300 DPI is the
 * resolution Tesseract's documentation recommends for reliable results on
 * printed text.</p>
 *
 * <p>If the executable is absent the engine reports itself unavailable and the
 * document is routed to the manual review queue, which is the OCR failure path
 * specified in the Milestone 2 architecture.</p>
 */
@Component
@Order(2)
public class TesseractOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrEngine.class);

    /** Resolution used when rasterising PDF pages before recognition. */
    private static final int RENDER_DPI = 300;

    /** Maximum number of PDF pages recognised, which bounds prototype runtime. */
    private static final int MAX_PAGES = 10;

    private final ApplicationProperties properties;

    /** Cached availability check so the executable is probed only once. */
    private Boolean available;

    public TesseractOcrEngine(ApplicationProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(Path file, String contentType) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".tif") || name.endsWith(".tiff")
                || name.endsWith(".bmp")
                || (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/"));
    }

    @Override
    public OcrResult extract(Path file) {
        long start = System.currentTimeMillis();
        if (!isAvailable()) {
            return OcrResult.empty("Tesseract executable not found; document routed to manual review.");
        }

        List<Path> temporaryFiles = new ArrayList<>();
        try {
            List<Path> images = file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")
                    ? renderPdfPages(file, temporaryFiles)
                    : List.of(file);

            if (images.isEmpty()) {
                return OcrResult.empty("No page images could be produced from the document.");
            }

            StringBuilder combined = new StringBuilder();
            for (Path image : images) {
                combined.append(runTesseract(image)).append(System.lineSeparator());
            }

            String text = combined.toString().strip();
            long elapsed = System.currentTimeMillis() - start;
            if (text.isBlank()) {
                return new OcrResult("", OcrResult.ENGINE_NONE, 0.0, elapsed,
                        "Optical recognition returned no text.");
            }
            return new OcrResult(text, OcrResult.ENGINE_TESSERACT, estimateConfidence(text), elapsed, null);

        } catch (Exception ex) {
            log.warn("Tesseract extraction failed for {}: {}", file.getFileName(), ex.toString());
            return OcrResult.empty("Optical recognition failed: " + ex.getClass().getSimpleName());
        } finally {
            temporaryFiles.forEach(this::deleteQuietly);
        }
    }

    /**
     * Rasterises the first pages of a PDF to temporary PNG files.
     *
     * @param pdf       the source document
     * @param collected list that receives every temporary file created, so the
     *                  caller can delete them even if recognition throws
     * @return the rendered page images in page order
     * @throws Exception if the PDF cannot be opened or a page cannot be written
     */
    private List<Path> renderPdfPages(Path pdf, List<Path> collected) throws Exception {
        List<Path> images = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), MAX_PAGES);
            for (int page = 0; page < pages; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, RENDER_DPI, ImageType.GRAY);
                Path target = Files.createTempFile("cred-ocr-", "-p" + page + ".png");
                collected.add(target);
                ImageIO.write(image, "png", target.toFile());
                images.add(target);
            }
        }
        return images;
    }

    /**
     * Runs the Tesseract executable against one image and returns its text.
     *
     * <p>Output is written to a temporary file rather than read from standard
     * output, because writing to a file is the behaviour Tesseract supports
     * identically across versions and platforms.</p>
     *
     * @param image page image to recognise
     * @return recognised text, empty when the process produced nothing
     * @throws Exception if the process cannot be started or times out
     */
    private String runTesseract(Path image) throws Exception {
        Path outputBase = Files.createTempFile("cred-ocr-out-", "");
        Files.deleteIfExists(outputBase); // Tesseract appends its own ".txt".
        File produced = new File(outputBase.toString() + ".txt");

        ProcessBuilder builder = new ProcessBuilder(
                properties.getOcr().getTesseractCommand(),
                image.toAbsolutePath().toString(),
                outputBase.toAbsolutePath().toString(),
                "-l", properties.getOcr().getLanguage());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        boolean finished = process.waitFor(properties.getOcr().getTimeoutSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Tesseract timed out after "
                    + properties.getOcr().getTimeoutSeconds() + " seconds");
        }

        try {
            return produced.exists()
                    ? Files.readString(produced.toPath(), StandardCharsets.UTF_8)
                    : "";
        } finally {
            deleteQuietly(produced.toPath());
        }
    }

    /**
     * Estimates confidence from the shape of the recognised text.
     *
     * <p>Tesseract's per-word confidence is only exposed through its TSV output
     * mode. For the prototype a proxy is used instead: the proportion of
     * characters that are letters, digits, or ordinary punctuation. Recognition
     * of a poor-quality scan produces a high share of stray symbols, which drives
     * this ratio down and, in turn, lowers the confidence shown to the reviewer.</p>
     *
     * @param text recognised text
     * @return a confidence estimate between 0.30 and 0.95
     */
    private double estimateConfidence(String text) {
        if (text.isEmpty()) {
            return 0.0;
        }
        long recognisable = text.chars()
                .filter(c -> Character.isLetterOrDigit(c) || Character.isWhitespace(c) || ".,-/#:()&'".indexOf(c) >= 0)
                .count();
        double ratio = (double) recognisable / text.length();
        return Math.max(0.30, Math.min(0.95, ratio));
    }

    @Override
    public String name() {
        return OcrResult.ENGINE_TESSERACT;
    }

    @Override
    public synchronized boolean isAvailable() {
        if (available != null) {
            return available;
        }
        try {
            Process probe = new ProcessBuilder(properties.getOcr().getTesseractCommand(), "--version")
                    .redirectErrorStream(true).start();
            available = probe.waitFor(10, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (Exception ex) {
            available = false;
        }
        if (!available) {
            log.warn("Tesseract OCR executable '{}' was not found. Image-only documents "
                            + "will be routed to the manual review queue.",
                    properties.getOcr().getTesseractCommand());
        }
        return available;
    }

    /** Deletes a temporary file, ignoring failures that do not affect the result. */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Temporary files are recreated per run; a failure here is not actionable.
        }
    }
}
