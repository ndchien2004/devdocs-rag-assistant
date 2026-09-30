package com.chien.devdocs.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Mọi lỗi trả về dạng Problem Details (RFC 9457).
 * {@link ResponseEntityExceptionHandler} đã lo: validation @RequestBody (400), MaxUploadSizeExceeded (413),
 * và mọi {@link org.springframework.web.ErrorResponseException} (các {@link ApiException} của dự án).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Validate @RequestParam / @PathVariable qua @Validated. */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setTitle("Validation failed");
        return pd;
    }

    /** Lỗi không lường trước: không lộ stack trace ra ngoài; lỗi kết nối Ollama → 503. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        if (AiServiceUnavailableException.isConnectivityProblem(ex)) {
            log.warn("AI service unavailable: {}", ex.getMessage());
            return new AiServiceUnavailableException(ex).getBody();
        }
        log.error("Unexpected error", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Đã có lỗi xảy ra, vui lòng thử lại sau.");
        pd.setTitle("Internal error");
        return pd;
    }
}
