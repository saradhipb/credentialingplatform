package edu.gcu.capstone.credentialing.workflow;

import edu.gcu.capstone.credentialing.config.ApplicationProperties;
import edu.gcu.capstone.credentialing.exception.ValidationException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * DocumentStorageService.java
 *
 * <p>Writes uploaded credential documents to disk and removes them again when
 * the retention window closes.</p>
 *
 * <p>Two safeguards are applied at intake. The file extension is checked against
 * an allow-list rather than a block-list, so an unexpected type is rejected by
 * default. And the name under which the file is written is generated, never
 * taken from the upload: a supplied name such as
 * {@code ../../config/application.yml} is a path traversal attempt, and
 * discarding the name entirely removes the class of problem instead of trying to
 * sanitise it.</p>
 */
@Service
public class DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);

    /** File types the platform is able to process. */
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "png", "jpg", "jpeg", "tif", "tiff", "bmp", "txt");

    private final ApplicationProperties properties;
    private Path uploadDirectory;

    public DocumentStorageService(ApplicationProperties properties) {
        this.properties = properties;
    }

    /** Creates the upload directory if it does not yet exist. */
    @PostConstruct
    void prepareStorage() {
        try {
            uploadDirectory = Paths.get(properties.getStorage().getUploadDir())
                    .toAbsolutePath().normalize();
            Files.createDirectories(uploadDirectory);
            log.info("Uploaded documents will be stored in {}", uploadDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create the upload directory", ex);
        }
    }

    /**
     * Validates and stores an uploaded file.
     *
     * @param file the multipart upload
     * @return the generated name under which the file was written
     * @throws ValidationException when the file is empty or of an unsupported type
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Select a file before choosing Upload.");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ValidationException("Files of type '" + extension + "' cannot be processed. "
                    + "Supported types are PDF, PNG, JPG, TIFF, BMP and TXT.");
        }

        String storedName = UUID.randomUUID() + "." + extension;
        Path target = uploadDirectory.resolve(storedName);

        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("The uploaded file could not be saved", ex);
        }
        return storedName;
    }

    /**
     * Resolves a stored file name to its path on disk.
     *
     * @param storedName the generated name returned by {@link #store}
     * @return the absolute path
     */
    public Path pathOf(String storedName) {
        return uploadDirectory.resolve(storedName);
    }

    /**
     * Deletes a stored file.
     *
     * @param storedName the generated name
     * @return true when a file was removed
     */
    public boolean delete(String storedName) {
        try {
            return Files.deleteIfExists(pathOf(storedName));
        } catch (IOException ex) {
            log.warn("Unable to delete stored document {}: {}", storedName, ex.toString());
            return false;
        }
    }

    /** @return the directory documents are written to, shown on the admin panel */
    public Path getUploadDirectory() {
        return uploadDirectory;
    }

    /**
     * Extracts a lower-case extension from a supplied file name.
     *
     * @param filename the original name, may be null
     * @return the extension without a dot, or an empty string
     */
    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
