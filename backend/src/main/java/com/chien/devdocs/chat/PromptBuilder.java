package com.chien.devdocs.chat;

import com.chien.devdocs.document.ChunkMetadata;
import org.springframework.ai.document.Document;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Ghép các chunk tìm được thành CONTEXT cho prompt (bước [4] luồng query).
 * <ul>
 *   <li>Đánh số [1]..[n] theo thứ tự score giảm dần, kèm (file — trang) để LLM trích dẫn.</li>
 *   <li>BR-QRY-04: tổng CONTEXT không vượt {@code maxTokens}; vượt thì bỏ chunk có score thấp nhất trước.</li>
 * </ul>
 */
@Component
public class PromptBuilder {

    private final ToIntFunction<String> tokenCounter;

    public PromptBuilder() {
        TokenCountEstimator estimator = new JTokkitTokenCountEstimator();
        this.tokenCounter = estimator::estimate;
    }

    PromptBuilder(ToIntFunction<String> tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    /**
     * @param text   CONTEXT đã đánh số
     * @param chunks các chunk thực sự nằm trong CONTEXT; chunk thứ i tương ứng trích dẫn [i+1]
     */
    public record BuiltContext(String text, List<Document> chunks) {
    }

    public BuiltContext buildContext(List<Document> hits, int maxTokens) {
        List<Document> kept = new ArrayList<>(hits.stream()
                .sorted(Comparator.comparing(Document::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList());

        // Bỏ chunk score thấp nhất (cuối danh sách) cho tới khi vừa ngân sách token. Luôn giữ ít nhất 1 chunk.
        while (kept.size() > 1 && tokenCounter.applyAsInt(render(kept)) > maxTokens) {
            kept.removeLast();
        }
        return new BuiltContext(render(kept), List.copyOf(kept));
    }

    private static String render(List<Document> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append('[').append(i + 1).append("] (").append(sourceLabel(chunks.get(i))).append(")\n")
                    .append(PromptInjectionGuard.sanitize(chunks.get(i).getText().strip()));
        }
        return sb.toString();
    }

    static String sourceLabel(Document chunk) {
        Object file = chunk.getMetadata().get(ChunkMetadata.FILE_NAME);
        Object page = chunk.getMetadata().get(ChunkMetadata.PAGE_NUMBER);
        Object section = chunk.getMetadata().get(ChunkMetadata.SECTION_TITLE);
        String location = section != null ? "mục " + page + ": " + section : "trang " + page;
        return file + " — " + location;
    }
}
