package com.chien.devdocs.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DuplicateDocumentException extends ApiException {

    public DuplicateDocumentException(UUID existingDocumentId) {
        super(HttpStatus.CONFLICT, "Duplicate document", "File này đã được index trước đó.");
        getBody().setProperty("existingDocumentId", existingDocumentId);
    }
}
