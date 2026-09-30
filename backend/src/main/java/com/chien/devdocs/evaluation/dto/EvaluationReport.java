package com.chien.devdocs.evaluation.dto;

import com.chien.devdocs.evaluation.EvalQuestion;
import com.chien.devdocs.evaluation.RetrievalMetrics;

import java.util.List;
import java.util.Map;

/**
 * Kết quả một lần chạy eval: chỉ số tổng, chỉ số theo nhóm câu hỏi, chi tiết từng câu (câu nào miss).
 */
public record EvaluationReport(
        Config config,
        Summary summary,
        Map<EvalQuestion.Category, CategorySummary> byCategory,
        List<QuestionResult> details
) {
    public record Config(String label, int topK, double similarityThreshold, boolean useTopicFilter,
                         int questionCount) {
    }

    /**
     * Hit@K và MRR tính trên câu hỏi trong phạm vi; tỷ lệ tính theo %.
     *
     * @param inScopeFoundRate  % câu trong phạm vi có ít nhất 1 chunk vượt ngưỡng (không bị từ chối nhầm)
     * @param outOfScopeAccuracy % câu ngoài phạm vi bị từ chối đúng (0 chunk vượt ngưỡng → found = false)
     * @param avgLatencyMs      thời gian retrieve trung bình (embedding câu hỏi + tìm trong pgvector)
     */
    public record Summary(Double hitAt1, Double hitAt3, Double hitAt5, Double hitAtK, Double mrr,
                          Double inScopeFoundRate, Double outOfScopeAccuracy, double avgLatencyMs) {
    }

    public record CategorySummary(int count, Double hitAt1, Double hitAt5, Double mrr, Double foundRate) {
    }

    /**
     * @param firstRelevantRank vị trí chunk đúng đầu tiên (0 = không tìm thấy trong top-K)
     */
    public record QuestionResult(String id, EvalQuestion.Category category, String question,
                                 List<EvalQuestion.ExpectedSource> expected, boolean found, int firstRelevantRank,
                                 boolean correct, List<RetrievalMetrics.RankedHit> retrieved, long latencyMs) {
    }
}
