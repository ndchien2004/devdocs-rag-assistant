package com.chien.devdocs.common.exception;

import org.springframework.http.HttpStatus;

/** Request hợp lệ về cú pháp nhưng không xử lý được (400). */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }
}
