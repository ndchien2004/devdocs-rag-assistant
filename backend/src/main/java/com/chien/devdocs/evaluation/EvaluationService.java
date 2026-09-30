package com.chien.devdocs.evaluation;

import com.chien.devdocs.chat.Retriever;
import com.chien.devdocs.config.RagProperties;
import com.chien.devdocs.document.ChunkMetadata;
import com.chien.devdocs.evaluation.EvalQuestion.Category;
import com.chien.devdocs.evaluation.RetrievalMetrics.RankedHit;
import com.chien.devdocs.evaluation.dto.EvaluationReport;
import com.chien.devdocs.evaluation.dto.EvaluationReport.CategorySummary;
import com.chien.devdocs.evaluation.dto.EvaluationReport.QuestionResult;
import com.chien.devdocs.evaluation.dto.EvaluationReport.Summary;
import com.chien.devdocs.evaluation.dto.EvaluationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.chien.devdocs.evaluation.RetrievalMetrics.firstRelevantRank;
import static com.chien.devdocs.evaluation.RetrievalMetrics.hitAt;
import static com.chien.devdocs.evaluation.RetrievalMetrics.mean;
import static com.chien.devdocs.evaluation.RetrievalMetrics.rate;
import static com.chien.devdocs.evaluation.RetrievalMetrics.reciprocalRank;
import static com.chien.devdocs.evaluation.RetrievalMetrics.round;

/**
 * Phase 3 — đo chất lượng retrieval bằng số liệu (mục 7.4). Không gọi LLM nên chạy nhanh và không tốn phí.
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
    /** Luôn lấy ít nhất 5 kết quả để tính được Hit@1, Hit@3, Hit@5. */
    private static final int MIN_RANKED = 5;

    private final Retriever retriever;
    private final VectorStore vectorStore;
    private final EvalDataset dataset;
    private final RagProperties props;

    public EvaluationService(Retriever retriever, VectorStore vectorStore, EvalDataset dataset, RagProperties props) {
        this.retriever = retriever;
        this.vectorStore = vectorStore;
        this.dataset = dataset;
        this.props = props;
    }

    public EvaluationReport run(EvaluationRequest request) {
        return run(request, vectorStore, "default");
    }

    /**
     * Chạy eval trên một VectorStore bất kỳ — Phase 4 dùng để so sánh các bảng vector có chunk size khác nhau.
     */
    public EvaluationReport run(EvaluationRequest request, VectorStore store, String label) {
        int topK = request.topK() != null ? request.topK() : props.topK();
        double threshold = request.similarityThreshold() != null
                ? request.similarityThreshold() : props.similarityThreshold();
        boolean useTopic = Boolean.TRUE.equals(request.useTopicFilter());
        List<EvalQuestion> questions = dataset.load();

        List<QuestionResult> details = new ArrayList<>();
        for (EvalQuestion q : questions) {
            details.add(evaluate(q, store, topK, threshold, useTopic));
        }

        EvaluationReport report = new EvaluationReport(
                new EvaluationReport.Config(label, topK, threshold, useTopic, questions.size()),
                summarize(details, topK),
                byCategory(details, topK),
                details);
        log.info("Eval [{}] topK={} threshold={} → {}", label, topK, threshold, report.summary());
        return report;
    }

    private QuestionResult evaluate(EvalQuestion q, VectorStore store, int topK, double threshold, boolean useTopic) {
        long start = System.currentTimeMillis();
        List<Document> hits = retriever.retrieve(store, q.question(), useTopic ? q.topic() : null,
                Math.max(topK, MIN_RANKED), threshold);
        long latency = System.currentTimeMillis() - start;

        List<RankedHit> ranked = hits.stream().map(EvaluationService::toRankedHit).toList();
        // "found" theo đúng cấu hình topK người dùng chọn (RagService chỉ lấy topK chunk).
        boolean found = !ranked.isEmpty();
        if (q.outOfScope()) {
            return new QuestionResult(q.id(), q.category(), q.question(), List.of(), found, 0, !found,
                    ranked.subList(0, Math.min(topK, ranked.size())), latency);
        }
        int rank = firstRelevantRank(ranked, q.expected());
        return new QuestionResult(q.id(), q.category(), q.question(), q.expected(), found, rank,
                hitAt(topK, rank), ranked, latency);
    }

    static Summary summarize(List<QuestionResult> details, int topK) {
        List<QuestionResult> inScope = details.stream().filter(d -> d.category() != Category.OUT_OF_SCOPE).toList();
        List<QuestionResult> outOfScope = details.stream().filter(d -> d.category() == Category.OUT_OF_SCOPE).toList();
        Double mrr = mean(inScope.stream().map(d -> reciprocalRankAt(topK, d.firstRelevantRank())).toList());
        return new Summary(
                rate(inScope.stream().map(d -> hitAt(1, d.firstRelevantRank())).toList()),
                rate(inScope.stream().map(d -> hitAt(3, d.firstRelevantRank())).toList()),
                rate(inScope.stream().map(d -> hitAt(5, d.firstRelevantRank())).toList()),
                rate(inScope.stream().map(d -> hitAt(topK, d.firstRelevantRank())).toList()),
                mrr == null ? null : round(mrr, 3),
                rate(inScope.stream().map(QuestionResult::found).toList()),
                rate(outOfScope.stream().map(d -> !d.found()).toList()),
                round(details.stream().mapToLong(QuestionResult::latencyMs).average().orElse(0), 1));
    }

    /** MRR@K: chunk đúng nằm ngoài top-K thì hệ thống thật không thấy nó → tính 0. */
    private static double reciprocalRankAt(int topK, int rank) {
        return reciprocalRank(rank <= topK ? rank : 0);
    }

    static Map<Category, CategorySummary> byCategory(List<QuestionResult> details, int topK) {
        Map<Category, CategorySummary> result = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            Predicate<QuestionResult> inCategory = d -> d.category() == c;
            List<QuestionResult> group = details.stream().filter(inCategory).toList();
            if (group.isEmpty()) {
                continue;
            }
            boolean oos = c == Category.OUT_OF_SCOPE;
            Double mrr = oos ? null : mean(group.stream().map(d -> reciprocalRankAt(topK, d.firstRelevantRank())).toList());
            result.put(c, new CategorySummary(group.size(),
                    oos ? null : rate(group.stream().map(d -> hitAt(1, d.firstRelevantRank())).toList()),
                    oos ? null : rate(group.stream().map(d -> hitAt(5, d.firstRelevantRank())).toList()),
                    mrr == null ? null : round(mrr, 3),
                    rate(group.stream().map(QuestionResult::found).toList())));
        }
        return result;
    }

    private static RankedHit toRankedHit(Document d) {
        Object page = d.getMetadata().get(ChunkMetadata.PAGE_NUMBER);
        Integer pageNumber = page instanceof Number n ? n.intValue() : page == null ? null : Integer.valueOf(page.toString());
        return new RankedHit((String) d.getMetadata().get(ChunkMetadata.FILE_NAME), pageNumber,
                d.getScore() == null ? null : round(d.getScore(), 4));
    }
}
