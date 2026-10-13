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
 * <p>
 * {@code documents.storage_path} chỉ chứa tên file <b>tương đối</b> so với storage-dir, nên chuyển máy hoặc đổi
 * {@code STORAGE_DIR} không làm hỏng re-index / xóa.
 */
@Component
public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

    private final Path root;

    public FileStorage(RagProperties props) {
        this.root = Path.of(props.storageDir()).toAbsolutePath().normalize();
    }

    /**
     * @return giá trị lưu vào {@code documents.storage_path}: tên file, tương đối so với storage-dir
     */
    public String save(UUID documentId, FileType type, byte[] content) {
        String fileName = documentId + type.extension();
        try {
            Files.createDirectories(root);
            Files.write(root.resolve(fileName), content);
            return fileName;
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được file", e);
        }
    }

    /**
     * Đường dẫn thật của file. Bản ghi cũ lưu đường dẫn tuyệt đối: dùng luôn nếu file còn đó, không thì tìm theo
     * tên file trong storage-dir hiện tại (trường hợp đã chuyển thư mục lưu trữ).
     *
     * @throws IllegalArgumentException nếu đường dẫn tương đối trỏ ra ngoài storage-dir
     */
    public Path resolve(String storagePath) {
        Path path = Path.of(storagePath);
        if (path.isAbsolute()) {
            if (Files.exists(path)) {
                return path;
            }
            path = path.getFileName();
        }
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Đường dẫn nằm ngoài thư mục lưu trữ: " + storagePath);
        }
        return resolved;
    }

    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Không xóa được file {}: {}", storagePath, e.getMessage());
        }
    }
}
