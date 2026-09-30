package com.chien.devdocs.evaluation;

import com.chien.devdocs.chat.Retriever;
import com.chien.devdocs.config.RagProperties;
import com.chien.devdocs.evaluation.EvalQuestion.Category;
import com.chien.devdocs.evaluation.EvalQuestion.ExpectedSource;
import com.chien.devdocs.evaluation.dto.EvaluationReport;
import com.chien.devdocs.evaluation.dto.EvaluationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationServiceTest {

    private Retriever retriever;
    private EvalDataset dataset;
    private VectorStore store;
    private EvaluationService service;

    @BeforeEach
    void setUp() {
        retriever = mock(Retriever.class);
        dataset = mock(EvalDataset.class);
        store = mock(VectorStore.class);
        RagProperties props = new RagProperties(5, 0.5, 3000, 500, 0, 200, 10, 30,
                DataSize.ofMegabytes(20), "./storage");
        service = new EvaluationService(retriever, store, dataset, props);
    }

    private static Document hit(String file, int page, double score) {
        return Document.builder().text("x").metadata(Map.of("file_name", file, "page_number", page)).score(score).build();
    }

    private static EvalQuestion q(String id, Category category, String question, ExpectedSource... expected) {
        return new EvalQuestion(id, category, null, question, List.of(expected));
    }

    private void retrieverReturns(String question, Document... hits) {
        when(retriever.retrieve(eq(store), eq(question), any(), anyInt(), anyDouble())).thenReturn(List.of(hits));
    }

    @Test
    void computesHitAtKMrrAndOutOfScopeAccuracy() {
        var a = new ExpectedSource("a.pdf", 1);
        when(dataset.load()).thenReturn(List.of(
                q("Q1", Category.DIRECT, "rank1", a),         // đúng ở vị trí 1
                q("Q2", Category.DIRECT, "rank2", a),         // đúng ở vị trí 2
                q("Q3", Category.PARAPHRASE, "rank4", a),     // đúng ở vị trí 4
                q("Q4", Category.PARAPHRASE, "miss", a),      // không có chunk đúng
                q("Q5", Category.OUT_OF_SCOPE, "oos-ok"),     // 0 hit → từ chối đúng
                q("Q6", Category.OUT_OF_SCOPE, "oos-bad")));  // có hit → từ chối sai
        retrieverReturns("rank1", hit("a.pdf", 1, .9), hit("b.pdf", 1, .8));
        retrieverReturns("rank2", hit("b.pdf", 1, .9), hit("a.pdf", 1, .8));
        retrieverReturns("rank4", hit("b.pdf", 1, .9), hit("b.pdf", 2, .8), hit("a.pdf", 2, .7), hit("a.pdf", 1, .6));
        retrieverReturns("miss", hit("b.pdf", 1, .9));
        retrieverReturns("oos-ok");
        retrieverReturns("oos-bad", hit("b.pdf", 3, .55));

        EvaluationReport report = service.run(EvaluationRequest.defaults());
        EvaluationReport.Summary s = report.summary();

        assertThat(s.hitAt1()).isEqualTo(25.0);          // 1/4
        assertThat(s.hitAt3()).isEqualTo(50.0);          // 2/4
        assertThat(s.hitAt5()).isEqualTo(75.0);          // 3/4
        assertThat(s.hitAtK()).isEqualTo(75.0);          // K = 5
        assertThat(s.mrr()).isEqualTo(0.438);            // (1 + 1/2 + 1/4 + 0) / 4 = 0.4375
        assertThat(s.inScopeFoundRate()).isEqualTo(100.0);
        assertThat(s.outOfScopeAccuracy()).isEqualTo(50.0);

        assertThat(report.details()).filteredOn(d -> d.id().equals("Q4"))
                .singleElement().satisfies(d -> {
                    assertThat(d.correct()).isFalse();
                    assertThat(d.firstRelevantRank()).isZero();
                });
        assertThat(report.byCategory().get(Category.DIRECT).hitAt1()).isEqualTo(50.0);
        assertThat(report.byCategory().get(Category.PARAPHRASE).hitAt5()).isEqualTo(50.0);
    }

    @Test
    void mrrAndHitAtKRespectRequestedTopK() {
        var a = new ExpectedSource("a.pdf", 1);
        when(dataset.load()).thenReturn(List.of(q("Q1", Category.DIRECT, "rank4", a)));
        retrieverReturns("rank4", hit("b.pdf", 1, .9), hit("b.pdf", 2, .8), hit("b.pdf", 3, .7), hit("a.pdf", 1, .6));

        EvaluationReport report = service.run(new EvaluationRequest(3, 0.4, false));

        assertThat(report.config().topK()).isEqualTo(3);
        assertThat(report.config().similarityThreshold()).isEqualTo(0.4);
        assertThat(report.summary().hitAtK()).isEqualTo(0.0);   // rank 4 > K = 3
        assertThat(report.summary().hitAt5()).isEqualTo(100.0);
        assertThat(report.summary().mrr()).isEqualTo(0.0);      // MRR@3
    }

    @Test
    void anyExpectedPageCountsAsRelevant() {
        var expected = List.of(new ExpectedSource("h.pdf", 5), new ExpectedSource("h.pdf", 6));
        var ranked = List.of(new RetrievalMetrics.RankedHit("h.pdf", 4, .9), new RetrievalMetrics.RankedHit("h.pdf", 6, .8));

        assertThat(RetrievalMetrics.firstRelevantRank(ranked, expected)).isEqualTo(2);
        assertThat(RetrievalMetrics.isRelevant(new RetrievalMetrics.RankedHit("other.pdf", 5, .9), expected)).isFalse();
    }

    @Test
    void bundledDatasetFollowsTheRequiredDistribution() throws IOException {
        List<EvalQuestion> questions = EvalDataset.parse(new ClassPathResource("eval/eval-dataset.json").getInputStream());

        assertThat(questions).hasSizeGreaterThanOrEqualTo(30);
        assertThat(questions).extracting(EvalQuestion::id).doesNotHaveDuplicates();
        assertThat(questions).filteredOn(q -> q.category() == Category.DIRECT).hasSizeGreaterThanOrEqualTo(12);
        assertThat(questions).filteredOn(q -> q.category() == Category.PARAPHRASE).hasSizeGreaterThanOrEqualTo(8);
        assertThat(questions).filteredOn(q -> q.category() == Category.CROSS_LINGUAL).hasSizeGreaterThanOrEqualTo(5);
        assertThat(questions).filteredOn(EvalQuestion::outOfScope).hasSizeGreaterThanOrEqualTo(5)
                .allMatch(q -> q.category() == Category.OUT_OF_SCOPE);
        assertThat(questions).filteredOn(q -> !q.outOfScope())
                .allSatisfy(q -> assertThat(q.topic()).isNotNull());
    }
}
