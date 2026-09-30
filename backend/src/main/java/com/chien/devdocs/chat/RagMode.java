package com.chien.devdocs.chat;

/**
 * Cách thực hiện RAG — để so sánh ở Phase 4 (thí nghiệm E5).
 */
public enum RagMode {
    /** Phase 2: tự retrieve, tự ghép prompt, không có chunk → không gọi LLM. */
    MANUAL,
    /** QuestionAnswerAdvisor dùng gần như mặc định: prompt template tiếng Anh của Spring AI, context không đánh số. */
    QA_ADVISOR,
    /** RetrievalAugmentationAdvisor cấu hình tương đương MANUAL: cùng prompt, cùng cách đánh số [n]. */
    RAG_ADVISOR
}
