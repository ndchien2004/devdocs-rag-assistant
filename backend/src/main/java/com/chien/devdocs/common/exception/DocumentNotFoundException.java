package com.chien.devdocs.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DocumentNotFoundException extends ApiException {

    public DocumentNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Document not found", "Không tìm thấy tài liệu " + id + ".");
    }
}
