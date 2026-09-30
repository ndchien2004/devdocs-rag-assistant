package com.chien.devdocs.evaluation;

import com.chien.devdocs.chat.ChatService;
import com.chien.devdocs.chat.RagAnswer;
import com.chien.devdocs.chat.RagMode;
import com.chien.devdocs.chat.dto.ChatRequest;
import com.chien.devdocs.chat.dto.SourceDto;
import com.chien.devdocs.evaluation.EvalQuestion.ExpectedSource;
import com.chien.devdocs.evaluation.dto.AnswerEvaluationReport;
import com.chien.devdocs.evaluation.dto.AnswerEvaluationReport.AnswerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.chien.devdocs.evaluation.RetrievalMetrics.rate;
import static com.chien.devdocs.evaluation.RetrievalMetrics.round;

/**
 * Phase 4 — thí nghiệm E5: so sánh RAG thủ công và RAG bằng Advisor ở mức <b>câu trả lời</b>.
 * <p>
 * Đo retrieval thôi thì vô nghĩa: cả ba cách đều gọi cùng {@code VectorStore.similaritySearch}. Khác biệt nằm ở
 * chỗ có gọi LLM hay không, LLM có trích dẫn không, nguồn trả về có đúng không, và độ trễ.
 * Có gọi LLM thật nên chạy chậm (vài giây / câu).
 */
@Service
public class AnswerEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AnswerEvaluationService.class);
    private static final Pattern CITATION = Pattern.compile("\\[\\d+(?:\\s*,\\s*\\d+)*]");

    private final ChatService chatService;
    private final EvalDataset dataset;

    public AnswerEvaluationService(ChatService chatService, EvalDataset dataset) {
        this.chatService = chatService;
        this.dataset = dataset;
    }

    public AnswerEvaluationReport run(RagMode mode, Integer topK) {
        List<AnswerResult> results = new ArrayList<>();
        for (EvalQuestion q : dataset.load()) {
            RagAnswer a = chatService.answer(new ChatRequest(q.question(), null, topK, mode));
            boolean cited = CITATION.matcher(a.answer()).find();
            boolean sourceCorrect = !q.outOfScope() && a.sources().stream().anyMatch(s -> matches(s, q.expected()));
            results.add(new AnswerResult(q.id(), q.category(), q.question(), a.found(), a.llmCalled(), cited,
                    sourceCorrect, a.sources().size(), a.latencyMs(), a.answer()));
            log.info("[{}] {} found={} cited={} sourceOk={} {} ms", mode, q.id(), a.found(), cited, sourceCorrect,
                    a.latencyMs());
        }
        return new AnswerEvaluationReport(mode, summarize(results), results);
    }

    static AnswerEvaluationReport.Summary summarize(List<AnswerResult> results) {
        List<AnswerResult> inScope = results.stream().filter(r -> r.category() != EvalQuestion.Category.OUT_OF_SCOPE).toList();
        List<AnswerResult> answered = inScope.stream().filter(AnswerResult::found).toList();
        List<AnswerResult> outOfScope = results.stream().filter(r -> r.category() == EvalQuestion.Category.OUT_OF_SCOPE).toList();
        return new AnswerEvaluationReport.Summary(
                rate(inScope.stream().map(AnswerResult::found).toList()),
                rate(answered.stream().map(AnswerResult::cited).toList()),
                rate(answered.stream().map(AnswerResult::sourceCorrect).toList()),
                answered.isEmpty() ? null : round(answered.stream().mapToInt(AnswerResult::sourceCount).average().orElse(0), 1),
                rate(outOfScope.stream().map(r -> !r.found()).toList()),
                outOfScope.stream().filter(AnswerResult::llmCalled).count(),
                round(inScope.stream().mapToLong(AnswerResult::latencyMs).average().orElse(0), 0),
                round(outOfScope.stream().mapToLong(AnswerResult::latencyMs).average().orElse(0), 0));
    }

    private static boolean matches(SourceDto s, List<ExpectedSource> expected) {
        return expected.stream().anyMatch(e -> e.fileName().equals(s.fileName())
                && s.pageNumber() != null && e.pageNumber() == s.pageNumber());
    }
}
