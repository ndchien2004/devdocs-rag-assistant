package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatResponse;
import com.chien.devdocs.chat.dto.SourceDto;

import java.util.List;

/**
 * Kết quả nội bộ của một lần hỏi — nhiều thông tin hơn {@link ChatResponse} để phục vụ đánh giá (Phase 4).
 */
public record RagAnswer(
        String answer,
        boolean found,
        List<SourceDto> sources,
        long latencyMs,
        boolean llmCalled,
        int retrievedCount
) {
    public ChatResponse toResponse() {
        return new ChatResponse(answer, found, sources, latencyMs);
    }
}
