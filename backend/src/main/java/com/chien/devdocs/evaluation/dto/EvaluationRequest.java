package com.chien.devdocs.evaluation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Cấu hình một lần chạy eval. Trường null = lấy giá trị mặc định trong {@code app.rag.*}.
 *
 * @param useTopicFilter true = lọc theo topic của từng câu hỏi (dễ hơn); false = tìm trên mọi tài liệu (mặc định)
 */
public record EvaluationRequest(
        @Min(1) @Max(20) Integer topK,
        @DecimalMin("0.0") @DecimalMax("1.0") Double similarityThreshold,
        Boolean useTopicFilter
) {
    public static EvaluationRequest defaults() {
        return new EvaluationRequest(null, null, null);
    }
}
