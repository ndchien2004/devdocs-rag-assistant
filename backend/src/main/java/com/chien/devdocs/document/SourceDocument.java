package com.chien.devdocs.document;

import com.chien.devdocs.common.Topic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Một file tài liệu người dùng đã upload (bảng {@code documents}).
 * Các chunk của nó nằm trong bảng {@code vector_store}, liên kết logic qua metadata {@code document_id}.
 */
@Entity
@Table(name = "documents")
public class SourceDocument {

    @Id
    private UUID id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Topic topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    protected SourceDocument() {
    }

    public SourceDocument(UUID id, String fileName, String contentType, long fileSize,
                          String checksum, Topic topic, String storagePath) {
        this.id = id;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.topic = topic;
        this.storagePath = storagePath;
        this.status = DocumentStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
        this.errorMessage = null;
    }

    public void markIndexed(int pageCount, int chunkCount) {
        this.status = DocumentStatus.INDEXED;
        this.pageCount = pageCount;
        this.chunkCount = chunkCount;
        this.errorMessage = null;
        this.indexedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
        this.chunkCount = 0;
        this.indexedAt = null;
    }

    /**
     * Dùng khi nạp lại một file trước đó FAILED (cùng checksum): cập nhật thông tin upload mới.
     */
    public void resetForRetry(String fileName, String contentType, Topic topic, String storagePath) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.topic = topic;
        this.storagePath = storagePath;
        this.status = DocumentStatus.PENDING;
        this.errorMessage = null;
        this.pageCount = null;
        this.chunkCount = null;
        this.indexedAt = null;
    }

    public UUID getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getChecksum() {
        return checksum;
    }

    public Topic getTopic() {
        return topic;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }
}
