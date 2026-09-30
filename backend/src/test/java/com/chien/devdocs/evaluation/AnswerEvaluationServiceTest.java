package com.chien.devdocs.evaluation;

import com.chien.devdocs.chat.ChatService;
import com.chien.devdocs.chat.RagAnswer;
import com.chien.devdocs.chat.RagMode;
import com.chien.devdocs.chat.dto.SourceDto;
import com.chien.devdocs.evaluation.EvalQuestion.Category;
import com.chien.devdocs.evaluation.EvalQuestion.ExpectedSource;
import com.chien.devdocs.evaluation.dto.AnswerEvaluationReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnswerEvaluationServiceTest {

    private static SourceDto source(String file, int page) {
        return new SourceDto(1, "d", file, page, null, "snippet", 0.8);
    }

    @Test
    void measuresAnsweringCitationsSourcesAndRefusals() {
        ChatService chat = mock(ChatService.class);
        EvalDataset dataset = mock(EvalDataset.class);
        var expected = List.of(new ExpectedSource("a.pdf", 5));
        when(dataset.load()).thenReturn(List.of(
                new EvalQuestion("Q1", Category.DIRECT, null, "cited-correct", expected),
                new EvalQuestion("Q2", Category.DIRECT, null, "uncited-wrong-source", expected),
                new EvalQuestion("Q3", Category.PARAPHRASE, null, "refused", expected),
                new EvalQuestion("Q4", Category.OUT_OF_SCOPE, null, "oos-no-llm", List.of()),
                new EvalQuestion("Q5", Category.OUT_OF_SCOPE, null, "oos-llm-answered", List.of())));
        when(chat.answer(argThat(r -> r != null && r.question().equals("cited-correct"))))
                .thenReturn(new RagAnswer("Đúng [1].", true, List.of(source("a.pdf", 5)), 100, true, 3));
        when(chat.answer(argThat(r -> r != null && r.question().equals("uncited-wrong-source"))))
                .thenReturn(new RagAnswer("Không trích dẫn.", true,
                        List.of(source("b.pdf", 1), source("b.pdf", 2), source("b.pdf", 3)), 300, true, 3));
        when(chat.answer(argThat(r -> r != null && r.question().equals("refused"))))
                .thenReturn(new RagAnswer("không tìm thấy", false, List.of(), 200, true, 2));
        when(chat.answer(argThat(r -> r != null && r.question().equals("oos-no-llm"))))
                .thenReturn(new RagAnswer("không tìm thấy", false, List.of(), 20, false, 0));
        when(chat.answer(argThat(r -> r != null && r.question().equals("oos-llm-answered"))))
                .thenReturn(new RagAnswer("Bịa ra câu trả lời", true, List.of(), 900, true, 0));

        AnswerEvaluationReport report = new AnswerEvaluationService(chat, dataset).run(RagMode.MANUAL, null);
        AnswerEvaluationReport.Summary s = report.summary();

        assertThat(s.inScopeAnsweredRate()).isEqualTo(66.7);     // 2/3
        assertThat(s.citationRate()).isEqualTo(50.0);            // 1/2 câu đã trả lời
        assertThat(s.sourceAccuracy()).isEqualTo(50.0);          // 1/2
        assertThat(s.avgSourceCount()).isEqualTo(2.0);           // (1 + 3) / 2
        assertThat(s.outOfScopeRefusalRate()).isEqualTo(50.0);   // 1/2
        assertThat(s.outOfScopeLlmCalls()).isEqualTo(1);
        assertThat(s.avgInScopeLatencyMs()).isEqualTo(200.0);
        assertThat(s.avgOutOfScopeLatencyMs()).isEqualTo(460.0);
    }
}
