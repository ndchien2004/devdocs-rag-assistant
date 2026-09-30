package com.chien.devdocs.evaluation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Đọc bộ câu hỏi đánh giá từ {@code app.rag.eval-dataset} (mặc định {@code classpath:eval/eval-dataset.json}).
 */
@Component
public class EvalDataset {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Resource resource;

    public EvalDataset(@Value("${app.rag.eval-dataset:classpath:eval/eval-dataset.json}") Resource resource) {
        this.resource = resource;
    }

    public List<EvalQuestion> load() {
        try (InputStream in = resource.getInputStream()) {
            return parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được bộ eval " + resource, e);
        }
    }

    static List<EvalQuestion> parse(InputStream in) {
        return JSON.readValue(in, new TypeReference<>() {
        });
    }
}
