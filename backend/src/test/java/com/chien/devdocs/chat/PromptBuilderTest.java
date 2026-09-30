package com.chien.devdocs.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private static Document chunk(String text, String file, int page, double score) {
        return Document.builder().text(text)
                .metadata(Map.of("file_name", file, "page_number", page))
                .score(score).build();
    }

    @Test
    void numbersChunksByDescendingScoreWithSourceLabels() {
        var builder = new PromptBuilder();
        var hits = List.of(
                chunk("REQUIRES_NEW luôn tạo transaction mới.", "02_Spring_Boot.pdf", 48, 0.77),
                chunk("REQUIRED tham gia transaction hiện có.", "02_Spring_Boot.pdf", 47, 0.82));

        PromptBuilder.BuiltContext ctx = builder.buildContext(hits, 3000);

        assertThat(ctx.text()).isEqualTo("""
                [1] (02_Spring_Boot.pdf — trang 47)
                REQUIRED tham gia transaction hiện có.

                [2] (02_Spring_Boot.pdf — trang 48)
                REQUIRES_NEW luôn tạo transaction mới.""");
        assertThat(ctx.chunks()).extracting(Document::getScore).containsExactly(0.82, 0.77);
    }

    @Test
    void markdownChunksShowSectionTitle() {
        Document md = Document.builder().text("HashMap dùng bảng băm.")
                .metadata(Map.of("file_name", "01_Java_Core.md", "page_number", 6, "section_title", "HashMap"))
                .score(0.9).build();

        assertThat(PromptBuilder.sourceLabel(md)).isEqualTo("01_Java_Core.md — mục 6: HashMap");
    }

    @Test
    void dropsLowestScoreChunksFirstWhenOverTokenBudget() {
        // 1 token / ký tự để dễ tính: mỗi chunk ~ 50 "token" sau khi render
        var builder = new PromptBuilder(String::length);
        String body = "x".repeat(30);
        var hits = List.of(
                chunk(body, "a.pdf", 1, 0.60),
                chunk(body, "a.pdf", 2, 0.90),
                chunk(body, "a.pdf", 3, 0.75));

        PromptBuilder.BuiltContext ctx = builder.buildContext(hits, 130);

        assertThat(ctx.chunks()).extracting(Document::getScore).containsExactly(0.90, 0.75);
        assertThat(ctx.text()).contains("[1] (a.pdf — trang 2)").contains("[2] (a.pdf — trang 3)")
                .doesNotContain("trang 1)");
    }

    @Test
    void alwaysKeepsAtLeastTheBestChunk() {
        var builder = new PromptBuilder();
        var hits = List.of(chunk("một đoạn rất dài ".repeat(500), "a.pdf", 1, 0.9));

        assertThat(builder.buildContext(hits, 10).chunks()).hasSize(1);
    }
}
