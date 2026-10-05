package com.chien.devdocs.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidDocumentScopeException extends ApiException {
    public InvalidDocumentScopeException() {
        super(HttpStatus.BAD_REQUEST, "Invalid document scope", "Phạm vi chỉ được gồm tài liệu đã index.");
    }
}
