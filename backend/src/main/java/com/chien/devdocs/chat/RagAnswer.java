package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatResponse;
import com.chien.devdocs.chat.dto.SourceDto;

import java.util.List;

/**
 * Kết quả nội bộ của một lần hỏi — nhiều thông tin hơn {@link ChatResponse} để phục vụ đánh giá (Phase 4).
 *
 * @param answer câu trả lời gốc của LLM, kể cả khi đó là câu từ chối — giữ nguyên để báo cáo E5 thấy được
 *               cách mỗi mode từ chối. API luôn trả câu chuẩn {@link ChatResponse#NOT_FOUND_ANSWER} khi không tìm thấy.
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
        return new ChatResponse(found ? answer : ChatResponse.NOT_FOUND_ANSWER, found, sources, latencyMs);
    }
}
