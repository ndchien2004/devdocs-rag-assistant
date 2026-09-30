package com.chien.devdocs.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Lỗi nghiệp vụ trả về theo chuẩn RFC 9457 Problem Details.
 * {@link ErrorResponseException} được {@code ResponseEntityExceptionHandler} xử lý sẵn.
 */
public abstract class ApiException extends ErrorResponseException {

    protected ApiException(HttpStatus status, String title, String detail, Throwable cause) {
        super(status, problem(status, title, detail), cause);
    }

    protected ApiException(HttpStatus status, String title, String detail) {
        this(status, title, detail, null);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }

    @Override
    public String getMessage() {
        return getBody().getDetail();
    }
}
