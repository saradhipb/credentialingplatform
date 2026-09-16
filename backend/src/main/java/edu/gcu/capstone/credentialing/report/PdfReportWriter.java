package edu.gcu.capstone.credentialing.report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * PdfReportWriter.java
 *
 * <p>A small layout helper that turns headings, label/value pairs and simple
 * tables into a paginated PDF using Apache PDFBox.</p>
 *
 * <p>Writing the layout by hand, rather than adding a reporting framework, keeps
 * the dependency list short and the output entirely predictable, which matters
 * for a document that is filed as compliance evidence. The class tracks the
 * vertical cursor and starts a new page automatically when content would run
 * past the bottom margin.</p>
 */
public class PdfReportWriter implements AutoCloseable {

    private static final PDRectangle PAGE_SIZE = PDRectangle.LETTER;
    private static final float MARGIN = 54f;          // 0.75 inch
    private static final float LINE_HEIGHT = 15f;
    private static final float BODY_SIZE = 10f;

    private static final PDFont BODY_FONT = PDType1Font.HELVETICA;
    private static final PDFont BOLD_FONT = PDType1Font.HELVETICA_BOLD;

    private final PDDocument document = new PDDocument();
    private PDPageContentStream content;
    private float cursorY;

    /**
     * Starts a new report and opens its first page.
     *
     * @throws IOException if the first page cannot be created
     */
    public PdfReportWriter() throws IOException {
        newPage();
    }

    /**
     * Writes the report title and a subtitle line.
     *
     * @param title    the report name
     * @param subtitle context such as the generation time and who generated it
     * @throws IOException on a write failure
     */
    public void title(String title, String subtitle) throws IOException {
        writeLine(title, BOLD_FONT, 16f);
        writeLine(subtitle, BODY_FONT, 9f);
        gap(8f);
        horizontalRule();
        gap(8f);
    }

    /**
     * Writes a section heading.
     *
     * @param heading the heading text
     * @throws IOException on a write failure
     */
    public void section(String heading) throws IOException {
        gap(6f);
        writeLine(heading, BOLD_FONT, 12f);
        gap(2f);
    }

    /**
     * Writes a label and its value on one line, wrapping a long value.
     *
     * @param label the field name
     * @param value the field value; null is rendered as an em dash
     * @throws IOException on a write failure
     */
    public void field(String label, String value) throws IOException {
        String text = label + ": " + (value == null || value.isBlank() ? "-" : value);
        for (String line : wrap(text, BODY_FONT, BODY_SIZE, PAGE_SIZE.getWidth() - 2 * MARGIN)) {
            writeLine(line, BODY_FONT, BODY_SIZE);
        }
    }

    /**
     * Writes a simple fixed-column table.
     *
     * @param headers     column headings
     * @param rows        row values, each the same length as {@code headers}
     * @param columnWidths width of each column in points
     * @throws IOException on a write failure
     */
    public void table(String[] headers, List<String[]> rows, float[] columnWidths) throws IOException {
        writeRow(headers, columnWidths, BOLD_FONT);
        horizontalRule();
        for (String[] row : rows) {
            writeRow(row, columnWidths, BODY_FONT);
        }
    }

    /**
     * Writes a paragraph of body text, wrapped to the page width.
     *
     * @param text the paragraph
     * @throws IOException on a write failure
     */
    public void paragraph(String text) throws IOException {
        for (String line : wrap(text, BODY_FONT, BODY_SIZE, PAGE_SIZE.getWidth() - 2 * MARGIN)) {
            writeLine(line, BODY_FONT, BODY_SIZE);
        }
        gap(4f);
    }

    /**
     * Finishes the document and returns its bytes.
     *
     * @return the rendered PDF
     * @throws IOException on a write failure
     */
    public byte[] toByteArray() throws IOException {
        if (content != null) {
            content.close();
            content = null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        if (content != null) {
            content.close();
            content = null;
        }
        document.close();
    }

    // ----- internal layout helpers ----------------------------------------

    /** Opens a new page and resets the vertical cursor to the top margin. */
    private void newPage() throws IOException {
        if (content != null) {
            content.close();
        }
        PDPage page = new PDPage(PAGE_SIZE);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        cursorY = PAGE_SIZE.getHeight() - MARGIN;
    }

    /** Writes one line, starting a new page first if the line would not fit. */
    private void writeLine(String text, PDFont font, float size) throws IOException {
        if (cursorY - LINE_HEIGHT < MARGIN) {
            newPage();
        }
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(MARGIN, cursorY);
        content.showText(sanitise(text));
        content.endText();
        cursorY -= LINE_HEIGHT;
    }

    /** Writes one table row across the supplied column widths. */
    private void writeRow(String[] values, float[] widths, PDFont font) throws IOException {
        if (cursorY - LINE_HEIGHT < MARGIN) {
            newPage();
        }
        float x = MARGIN;
        for (int i = 0; i < values.length && i < widths.length; i++) {
            String cell = truncateToWidth(values[i] == null ? "-" : values[i], font, 9f, widths[i] - 6f);
            content.beginText();
            content.setFont(font, 9f);
            content.newLineAtOffset(x, cursorY);
            content.showText(sanitise(cell));
            content.endText();
            x += widths[i];
        }
        cursorY -= LINE_HEIGHT;
    }

    /** Draws a rule the width of the text area. */
    private void horizontalRule() throws IOException {
        if (cursorY - 4 < MARGIN) {
            newPage();
        }
        content.moveTo(MARGIN, cursorY + 4);
        content.lineTo(PAGE_SIZE.getWidth() - MARGIN, cursorY + 4);
        content.stroke();
        cursorY -= 4;
    }

    /** Advances the cursor without writing anything. */
    private void gap(float points) {
        cursorY -= points;
    }

    /**
     * Splits text into lines that fit the available width.
     *
     * @param text      the text to wrap
     * @param font      the font it will be drawn in
     * @param size      the point size
     * @param maxWidth  the available width in points
     * @return the wrapped lines
     */
    private List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : sanitise(text).split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.getStringWidth(candidate) / 1000 * size > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    /** Shortens a cell value with an ellipsis so it fits its column. */
    private String truncateToWidth(String text, PDFont font, float size, float maxWidth)
            throws IOException {
        String value = sanitise(text);
        if (font.getStringWidth(value) / 1000 * size <= maxWidth) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        for (char c : value.toCharArray()) {
            String candidate = builder.toString() + c + "...";
            if (font.getStringWidth(candidate) / 1000 * size > maxWidth) {
                break;
            }
            builder.append(c);
        }
        return builder + "...";
    }

    /**
     * Replaces characters the standard PDF fonts cannot encode. Optical
     * recognition occasionally yields characters outside the WinAnsi range, and
     * PDFBox raises an exception rather than substituting them, so the
     * substitution is made here.
     *
     * @param text the raw text
     * @return text safe to draw
     */
    private String sanitise(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '\n' || c == '\r' || c == '\t') {
                builder.append(' ');
            } else if (c >= 32 && c <= 126) {
                builder.append(c);
            } else {
                builder.append('?');
            }
        }
        return builder.toString();
    }
}
