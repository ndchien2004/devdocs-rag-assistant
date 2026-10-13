package com.chien.devdocs.common.exception;

import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceUnavailableExceptionTest {

    @Test
    void blockingClientConnectionAndTimeoutErrorsAreConnectivityProblems() {
        assertThat(AiServiceUnavailableException.isConnectivityProblem(
                new ResourceAccessException("I/O error", new ConnectException("refused")))).isTrue();
        assertThat(AiServiceUnavailableException.isConnectivityProblem(
                new RuntimeException(new SocketTimeoutException("read timed out")))).isTrue();
    }

    @Test
    void streamingReadTimeoutBetweenTokensIsAConnectivityProblem() {
        // Đúng dạng lỗi thật khi Ollama ngừng gửi token quá spring.http.clients.read-timeout.
        var e = new WebClientResponseException(200, "OK", HttpHeaders.EMPTY, null, null);
        e.initCause(ReadTimeoutException.INSTANCE);

        assertThat(AiServiceUnavailableException.isConnectivityProblem(e)).isTrue();
    }

    @Test
    void ordinaryErrorsAreNot() {
        assertThat(AiServiceUnavailableException.isConnectivityProblem(new IllegalStateException("bug"))).isFalse();
        assertThat(AiServiceUnavailableException.isConnectivityProblem(null)).isFalse();
    }
}
