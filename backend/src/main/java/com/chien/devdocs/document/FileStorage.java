package com.chien.devdocs.document;

import com.chien.devdocs.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Lưu file gốc trên đĩa ({@code app.rag.storage-dir}) để có thể re-index sau này.
 * Tên file lưu = {@code <documentId><ext>}, không dùng tên người dùng gửi lên (tránh path traversal).
 */
@Component
public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

    private final Path root;

    public FileStorage(RagProperties props) {
        this.root = Path.of(props.storageDir()).toAbsolutePath().normalize();
    }

    public Path save(UUID documentId, FileType type, byte[] content) {
        try {
            Files.createDirectories(root);
            Path target = root.resolve(documentId + type.extension());
            Files.write(target, content);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được file", e);
        }
    }

    public Path resolve(String storagePath) {
        return Path.of(storagePath);
    }

    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(Path.of(storagePath));
        } catch (IOException e) {
            log.warn("Không xóa được file {}: {}", storagePath, e.getMessage());
        }
    }
}
