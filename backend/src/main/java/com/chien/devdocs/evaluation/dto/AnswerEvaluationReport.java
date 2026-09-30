package com.chien.devdocs.evaluation.dto;

import com.chien.devdocs.chat.RagMode;
import com.chien.devdocs.evaluation.EvalQuestion;

import java.util.List;

public record AnswerEvaluationReport(RagMode mode, Summary summary, List<AnswerResult> details) {

    /**
     * @param inScopeAnsweredRate  % câu trong phạm vi được trả lời (không bị từ chối)
     * @param citationRate         % câu đã trả lời có ít nhất một trích dẫn [n]
     * @param sourceAccuracy       % câu đã trả lời mà danh sách sources có chứa trang đúng
     * @param avgSourceCount       số nguồn trung bình trả về cho mỗi câu đã trả lời (ít = lọc nguồn tốt)
     * @param outOfScopeRefusalRate % câu ngoài phạm vi bị từ chối đúng
     * @param outOfScopeLlmCalls   số câu ngoài phạm vi vẫn gọi LLM (lý tưởng = 0, BR-QRY-03)
     */
    public record Summary(Double inScopeAnsweredRate, Double citationRate, Double sourceAccuracy,
                          Double avgSourceCount, Double outOfScopeRefusalRate, long outOfScopeLlmCalls,
                          double avgInScopeLatencyMs, double avgOutOfScopeLatencyMs) {
    }

    public record AnswerResult(String id, EvalQuestion.Category category, String question, boolean found,
                               boolean llmCalled, boolean cited, boolean sourceCorrect, int sourceCount,
                               long latencyMs, String answer) {
    }
}
