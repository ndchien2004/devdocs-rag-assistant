package com.chien.devdocs.chat.dto;

import com.chien.devdocs.chat.RagMode;
import com.chien.devdocs.common.Topic;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param topic bỏ trống = tìm trên mọi tài liệu
 * @param topK  bỏ trống = {@code app.rag.top-k}
 * @param mode  bỏ trống = {@link RagMode#MANUAL}
 */
public record ChatRequest(
        @NotBlank @Size(max = 1000) String question,
        Topic topic,
        @Min(1) @Max(10) Integer topK,
        RagMode mode
) {
    public ChatRequest(String question, Topic topic, Integer topK) {
        this(question, topic, topK, null);
    }

    public int topKOrDefault(int defaultTopK) {
        return topK != null ? topK : defaultTopK;
    }

    public RagMode modeOrDefault() {
        return mode != null ? mode : RagMode.MANUAL;
    }
}
