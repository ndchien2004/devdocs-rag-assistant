package com.chien.devdocs.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DuplicateDocumentException extends ApiException {

    public DuplicateDocumentException(UUID existingDocumentId) {
        this(existingDocumentId, "File này đã được index trước đó.");
    }

    private DuplicateDocumentException(UUID existingDocumentId, String detail) {
        super(HttpStatus.CONFLICT, "Duplicate document", detail);
        getBody().setProperty("existingDocumentId", existingDocumentId);
    }

    /** Cùng file đang được một request khác index (PENDING / PROCESSING). */
    public static DuplicateDocumentException inProgress(UUID existingDocumentId) {
        return new DuplicateDocumentException(existingDocumentId,
                "File này đang được xử lý. Nếu bị kẹt, hãy gọi /reindex với existingDocumentId.");
    }
}
