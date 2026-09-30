package com.chien.devdocs.chat;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Nhận diện câu trả lời kiểu "không tìm thấy / không trả lời được" (BR-QRY-03).
 * Dùng chung cho mọi {@link RagMode} để so sánh công bằng: prompt mặc định của advisor khiến LLM từ chối
 * bằng câu chữ khác (thậm chí bằng tiếng Anh), không phải đúng câu trong system prompt của dự án.
 */
public final class RefusalDetector {

    private static final List<String> MARKERS = List.of(
            "không tìm thấy nội dung liên quan",
            "không tìm thấy thông tin",
            "không được tìm thấy",
            "không thể tìm thấy",
            "không có thông tin",
            "không thể trả lời",
            "không trả lời được",
            "nằm ngoài phạm vi",
            "can't answer", "cannot answer", "can not answer",
            "don't know", "do not know",
            "outside your knowledge base", "outside my knowledge base",
            "not in the context", "no information",
            // QuestionAnswerAdvisor với template mặc định tiếng Anh hay "rào đón" kiểu này (E5):
            "does not contain information", "does not contain a", "does not provide", "does not offer",
            "does not directly address");

    private RefusalDetector() {
    }

    public static boolean isRefusal(String answer) {
        if (answer == null || answer.isBlank()) {
            return true;
        }
        String normalized = Normalizer.normalize(answer, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace('’', '\'');
        return MARKERS.stream().anyMatch(normalized::contains);
    }
}
