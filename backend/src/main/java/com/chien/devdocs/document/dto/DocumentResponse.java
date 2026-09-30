package com.chien.devdocs.document.dto;

import com.chien.devdocs.common.Topic;
import com.chien.devdocs.document.DocumentStatus;
import com.chien.devdocs.document.SourceDocument;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String fileName,
        String contentType,
        long fileSize,
        Topic topic,
        DocumentStatus status,
        Integer pageCount,
        Integer chunkCount,
        String errorMessage,
        Instant createdAt,
        Instant indexedAt
) {
    public static DocumentResponse from(SourceDocument d) {
        return new DocumentResponse(d.getId(), d.getFileName(), d.getContentType(), d.getFileSize(), d.getTopic(),
                d.getStatus(), d.getPageCount(), d.getChunkCount(), d.getErrorMessage(), d.getCreatedAt(),
                d.getIndexedAt());
    }
}
