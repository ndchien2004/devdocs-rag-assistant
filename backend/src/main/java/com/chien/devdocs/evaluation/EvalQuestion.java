package com.chien.devdocs.evaluation;

import com.chien.devdocs.common.Topic;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Một câu hỏi trong bộ eval ({@code resources/eval/eval-dataset.json}).
 * {@code expected} rỗng = câu hỏi ngoài phạm vi, hệ thống phải trả found = false.
 */
public record EvalQuestion(
        String id,
        Category category,
        Topic topic,
        String question,
        List<ExpectedSource> expected
) {
    public enum Category { DIRECT, PARAPHRASE, CROSS_LINGUAL, OUT_OF_SCOPE }

    public record ExpectedSource(
            @JsonProperty("file_name") String fileName,
            @JsonProperty("page_number") int pageNumber
    ) {
    }

    public boolean outOfScope() {
        return expected == null || expected.isEmpty();
    }
}
