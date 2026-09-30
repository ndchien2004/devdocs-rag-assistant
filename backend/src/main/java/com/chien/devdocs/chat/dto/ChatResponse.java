package com.chien.devdocs.chat.dto;

import java.util.List;

public record ChatResponse(
        String answer,
        boolean found,
        List<SourceDto> sources,
        long latencyMs
) {
    public static final String NOT_FOUND_ANSWER = "Mình không tìm thấy nội dung liên quan trong tài liệu đã nạp.";

    public static ChatResponse notFound(long latencyMs) {
        return new ChatResponse(NOT_FOUND_ANSWER, false, List.of(), latencyMs);
    }
}
