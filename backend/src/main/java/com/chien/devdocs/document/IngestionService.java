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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
    static final String NO_TEXT_MESSAGE =
            "Không trích xuất được văn bản (có thể là PDF scan chỉ chứa ảnh — cần OCR, ngoài phạm vi dự án).";
    static final String AI_UNAVAILABLE_MESSAGE =
            "Không kết nối được tới mô hình AI (Ollama) khi tạo embedding. Kiểm tra Ollama rồi index lại tài liệu.";
    static final String INDEXING_FAILED_MESSAGE = "Không tạo được chỉ mục cho tài liệu. Hãy thử index lại.";
    static final String INTERRUPTED_MESSAGE = "Quá trình index bị gián đoạn. Hãy index lại tài liệu.";

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
        if (existing.isPresent() && existing.get().getStatus() != DocumentStatus.FAILED) {
            throw new DuplicateDocumentException(existing.get().getId());
        }

        // [3] Lưu bản ghi PENDING + file vật lý. Bản ghi cũ FAILED (cùng checksum) được dùng lại.
        SourceDocument doc;
        String path;
        if (existing.isPresent()) {
            doc = existing.get();
            path = storage.save(doc.getId(), type, content).toString();
            doc.resetForRetry(fileName, type.canonicalContentType(), topic, path);
            log.info("Re-uploading previously failed document {} ({})", doc.getId(), fileName);
        } else {
            UUID id = UUID.randomUUID();
            path = storage.save(id, type, content).toString();
            doc = new SourceDocument(id, fileName, type.canonicalContentType(), content.length, checksum, topic, path);
        }
        try {
            doc = repository.save(doc);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException concurrentDuplicate) {
            if (existing.isEmpty()) storage.delete(path);
            SourceDocument winner = repository.findByChecksum(checksum).orElse(null);
            if (winner != null) {
                throw new DuplicateDocumentException(winner.getId());
            }
            throw concurrentDuplicate;
        }

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
        SourceDocument doc = get(id);
        if (doc.getStatus() == DocumentStatus.PENDING || doc.getStatus() == DocumentStatus.PROCESSING) {
            throw new DuplicateDocumentException(id);
        }
        return index(doc);
    }

    /**
     * Ingestion chạy đồng bộ trong request, nên bản ghi còn PENDING/PROCESSING lúc khởi động là do backend bị dừng
     * giữa chừng. Chuyển về FAILED để nạp lại / re-index được (hai trạng thái kia đều bị chặn bằng 409).
     * Giả định mỗi database chỉ có một backend đang chạy.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedDocuments() {
        for (DocumentStatus stuck : List.of(DocumentStatus.PENDING, DocumentStatus.PROCESSING)) {
            for (SourceDocument doc : repository.search(null, stuck)) {
                doc.markFailed(INTERRUPTED_MESSAGE);
                repository.save(doc);
                log.warn("Document {} ({}) was left {} by a previous run → marked FAILED", doc.getId(),
                        doc.getFileName(), stuck);
            }
        }
    }

    // ------------------------------------------------------------------ pipeline [4] → [10]

    SourceDocument index(SourceDocument doc) {
        long start = System.currentTimeMillis();
        doc.markProcessing();
        try {
            doc = repository.save(doc);
        } catch (ObjectOptimisticLockingFailureException alreadyProcessing) {
            throw new DuplicateDocumentException(doc.getId());
        }
        boolean readingFile = false;
        try {
            // Xóa chunk cũ trước (re-index / nạp lại) để không bị nhân đôi.
            deleteChunks(doc.getId());

            readingFile = true;
            ChunkingPipeline.Result result = pipeline.chunk(doc);                          // [5] → [8]
            readingFile = false;
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
            log.error("Indexing failed for {} ({})", doc.getId(), doc.getFileName(), e);
            doc.markFailed(publicProcessingError(doc, e, readingFile));
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

    /**
     * Thông báo lưu vào {@code error_message} — không lộ nội dung exception.
     * Chọn theo bước bị lỗi: chỉ đổ cho file khi chính bước đọc file thất bại ({@code readingFile}).
     */
    private static String publicProcessingError(SourceDocument doc, RuntimeException error, boolean readingFile) {
        if (error instanceof InvalidFileException invalid) return invalid.getBody().getDetail();
        if (AiServiceUnavailableException.isConnectivityProblem(error)) return AI_UNAVAILABLE_MESSAGE;
        if (!readingFile) return INDEXING_FAILED_MESSAGE;
        if (FileType.fromFileName(doc.getFileName()).orElse(null) == FileType.PDF) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause.getClass().getSimpleName().contains("InvalidPassword")) {
                    return "PDF được bảo vệ bằng mật khẩu. Hãy gỡ mật khẩu rồi tải lên lại.";
                }
            }
            return "Không đọc được PDF. File có thể bị hỏng hoặc không được hỗ trợ; hãy mở lại file và tải bản PDF hợp lệ.";
        }
        return "Không thể xử lý tài liệu. Hãy kiểm tra file rồi thử tải lên lại.";
    }
}
