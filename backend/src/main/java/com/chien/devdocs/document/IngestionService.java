package com.chien.devdocs.document;

import com.chien.devdocs.common.Topic;
import com.chien.devdocs.common.exception.AiServiceUnavailableException;
import com.chien.devdocs.common.exception.DocumentNotFoundException;
import com.chien.devdocs.common.exception.DuplicateDocumentException;
import com.chien.devdocs.common.exception.FileTooLargeException;
import com.chien.devdocs.common.exception.InvalidFileException;
import com.chien.devdocs.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Luồng ingestion (mục 7.1): validate → checksum → lưu file → đọc → làm sạch → cắt chunk → gắn metadata → embedding + lưu.
 * <p>
 * Không đặt {@code @Transactional} trên cả luồng: mỗi lần đổi trạng thái được commit ngay,
 * để trạng thái FAILED vẫn được lưu khi bước embedding lỗi.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_ERROR_LENGTH = 1000;
    static final String NO_TEXT_MESSAGE =
            "Không trích xuất được văn bản (có thể là PDF scan chỉ chứa ảnh — cần OCR, ngoài phạm vi dự án).";

    private final SourceDocumentRepository repository;
    private final VectorStore vectorStore;
    private final ChunkingPipeline pipeline;
    private final FileStorage storage;
    private final RagProperties props;

    public IngestionService(SourceDocumentRepository repository, VectorStore vectorStore, ChunkingPipeline pipeline,
                            FileStorage storage, RagProperties props) {
        this.repository = repository;
        this.vectorStore = vectorStore;
        this.pipeline = pipeline;
        this.storage = storage;
        this.props = props;
    }

    // ------------------------------------------------------------------ upload

    public SourceDocument upload(MultipartFile file, Topic topic) {
        // [1] Validate (BR-ING-01)
        String fileName = originalFileName(file);
        FileType type = FileType.fromFileName(fileName)
                .orElseThrow(() -> new InvalidFileException("Định dạng không được hỗ trợ. Chỉ chấp nhận .pdf và .md."));
        if (!type.accepts(file.getContentType())) {
            throw new InvalidFileException("Content-Type '" + file.getContentType()
                    + "' không khớp với phần mở rộng " + type.extension() + ".");
        }
        if (file.getSize() > props.maxFileSize().toBytes()) {
            throw new FileTooLargeException(props.maxFileSize());
        }
        byte[] content = readBytes(file);
        if (content.length == 0) {
            throw new InvalidFileException("File rỗng.");
        }
        if (type == FileType.PDF && !startsWith(content, PDF_MAGIC)) {
            throw new InvalidFileException("File không phải PDF hợp lệ.");
        }

        // [2] Chống nạp trùng (BR-ING-02)
        String checksum = sha256(content);
        Optional<SourceDocument> existing = repository.findByChecksum(checksum);
        if (existing.isPresent() && existing.get().getStatus() == DocumentStatus.INDEXED) {
            throw new DuplicateDocumentException(existing.get().getId());
        }

        // [3] Lưu bản ghi PENDING + file vật lý. Bản ghi cũ FAILED (cùng checksum) được dùng lại.
        SourceDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
            String path = storage.save(doc.getId(), type, content).toString();
            doc.resetForRetry(fileName, type.canonicalContentType(), topic, path);
            log.info("Re-uploading previously failed document {} ({})", doc.getId(), fileName);
        } else {
            UUID id = UUID.randomUUID();
            String path = storage.save(id, type, content).toString();
            doc = new SourceDocument(id, fileName, type.canonicalContentType(), content.length, checksum, topic, path);
        }
        doc = repository.save(doc);

        return index(doc);
    }

    // ------------------------------------------------------------------ query / delete / reindex

    public List<SourceDocument> list(Topic topic, DocumentStatus status) {
        return repository.search(topic, status);
    }

    public SourceDocument get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    /** BR-ING-07: xóa chunk trong vector_store → xóa bản ghi → xóa file. */
    public void delete(UUID id) {
        SourceDocument doc = get(id);
        deleteChunks(id);
        repository.delete(doc);
        storage.delete(doc.getStoragePath());
        log.info("Deleted document {} ({})", id, doc.getFileName());
    }

    /** BR-ING-07: xóa chunk cũ rồi chạy lại pipeline từ bước [5]. */
    public SourceDocument reindex(UUID id) {
        return index(get(id));
    }

    // ------------------------------------------------------------------ pipeline [4] → [10]

    SourceDocument index(SourceDocument doc) {
        long start = System.currentTimeMillis();
        doc.markProcessing();
        doc = repository.save(doc);
        try {
            // Xóa chunk cũ trước (re-index / nạp lại) để không bị nhân đôi.
            deleteChunks(doc.getId());

            ChunkingPipeline.Result result = pipeline.chunk(doc);                          // [5] → [8]
            if (result.usablePages() == 0 || result.chunks().isEmpty()) {
                log.warn("Document {} has no extractable text", doc.getId());
                doc.markFailed(NO_TEXT_MESSAGE);
                return repository.save(doc);
            }
            List<Document> chunks = result.chunks();

            long embedStart = System.currentTimeMillis();
            vectorStore.add(chunks);                                                       // [9]
            long embedMs = System.currentTimeMillis() - embedStart;

            doc.markIndexed(result.pageCount(), chunks.size());                            // [10]
            doc = repository.save(doc);
            log.info("Indexed {} ({}): {} pages → {} usable pages → {} chunks, embed+store {} ms, total {} ms",
                    doc.getId(), doc.getFileName(), result.pageCount(), result.usablePages(), chunks.size(),
                    embedMs, System.currentTimeMillis() - start);
            return doc;
        } catch (RuntimeException e) {
            log.error("Indexing failed for {} ({}): {}", doc.getId(), doc.getFileName(), e.toString());
            doc.markFailed(truncate(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            doc = repository.save(doc);
            if (AiServiceUnavailableException.isConnectivityProblem(e)) {
                throw new AiServiceUnavailableException(e);
            }
            return doc;
        }
    }

    private void deleteChunks(UUID documentId) {
        // Dùng FilterExpressionBuilder thay vì ghép chuỗi → không có nguy cơ injection vào filter.
        vectorStore.delete(new FilterExpressionBuilder().eq(ChunkMetadata.DOCUMENT_ID, documentId.toString()).build());
    }

    // ------------------------------------------------------------------ helpers

    private static String originalFileName(MultipartFile file) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename()));
        if (!StringUtils.hasText(name)) {
            throw new InvalidFileException("Thiếu tên file.");
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file upload", e);
        }
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String truncate(String message) {
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH) + "…";
    }
}
