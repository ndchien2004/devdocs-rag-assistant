package com.chien.devdocs.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

public class FileTooLargeException extends ApiException {

    public FileTooLargeException(DataSize maxSize) {
        super(HttpStatus.CONTENT_TOO_LARGE, "File too large",
                "File vượt quá dung lượng cho phép (" + maxSize.toMegabytes() + " MB).");
    }
}
