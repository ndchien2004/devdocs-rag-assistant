package com.chien.devdocs.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Ollama / LLM / embedding model không phản hồi → 503.
 */
public class AiServiceUnavailableException extends ApiException {

    public AiServiceUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable",
                "Không kết nối được tới mô hình AI (Ollama). Vui lòng kiểm tra Ollama đã chạy và thử lại sau.",
                cause);
    }

    /**
     * Lỗi kết nối (connection refused, timeout...) nằm đâu đó trong chuỗi cause.
     */
    public static boolean isConnectivityProblem(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof java.net.ConnectException
                    || c instanceof java.net.SocketTimeoutException
                    || c instanceof java.net.http.HttpTimeoutException
                    || c instanceof java.net.UnknownHostException
                    || c instanceof org.springframework.web.client.ResourceAccessException
                    || c instanceof org.springframework.ai.retry.TransientAiException) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }
}
