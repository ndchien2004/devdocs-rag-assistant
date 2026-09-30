package com.chien.devdocs.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidFileException extends ApiException {

    public InvalidFileException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid file", detail);
    }
}
