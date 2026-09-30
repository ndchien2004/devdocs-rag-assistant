package com.chien.devdocs.document;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TextCleanerTest {

    private final TextCleaner cleaner = new TextCleaner();

    @Test
    void normalizesVietnameseToNfc() {
        String decomposed = Normalizer.normalize("Giao dịch bị hoàn tác, lệ phí", Normalizer.Form.NFD);
        assertThat(decomposed).isNotEqualTo("Giao dịch bị hoàn tác, lệ phí");

        assertThat(cleaner.clean(decomposed)).isEqualTo("Giao dịch bị hoàn tác, lệ phí");
    }

    @Test
    void collapsesRepeatedSpacesAndBlankLines() {
        String text = "Spring   Boot    là\t\tframework.   \n\n\n\nDòng   thứ hai.\n\n";
        assertThat(cleaner.clean(text)).isEqualTo("Spring Boot là framework.\n\nDòng thứ hai.");
    }

    @Test
    void keepsFencedCodeBlocksVerbatim() {
        String code = """
                ```java
                @Transactional(propagation = Propagation.REQUIRES_NEW)
                public void   save(Order o)  {


                    repo.save(o);   // giữ nguyên
                }
                ```""";
        String text = "Ví   dụ:\n\n\n" + code + "\n\n\nHết.";

        String cleaned = cleaner.clean(text);

        assertThat(cleaned).contains(code);
        assertThat(cleaned).startsWith("Ví dụ:\n\n```java");
        assertThat(cleaned).endsWith("```\n\nHết.");
    }

    @Test
    void keepsAnnotationsAndSpecialCharacters() {
        String text = "Dùng @Transactional và Map<String, List<Integer>> với a && b || !c; x -> x * 2";
        assertThat(cleaner.clean(text)).isEqualTo(text);
    }

    @Test
    void keepsLeadingIndentationOfCodeLikeLines() {
        String text = "public class A {\n    int x = 1;\n}";
        assertThat(cleaner.clean(text)).isEqualTo(text);
    }

    @Test
    void removesStandalonePageNumbers() {
        String text = "Nội dung trang.\n12\n- 13 -\nPage 14\nTrang 15\n16 / 300\nPage 3 of 10\nSố 42 trong câu thì giữ.";
        assertThat(cleaner.clean(text)).isEqualTo("Nội dung trang.\nSố 42 trong câu thì giữ.");
    }

    @Test
    void removesNulAndControlCharacters() {
        assertThat(cleaner.clean("abc\u0000def\u0007g")).isEqualTo("abcdefg");
    }

    @Test
    void removesHeaderAndFooterRepeatedAcrossPages() {
        List<String> topics = List.of("beans", "transactions", "proxies", "profiles", "validation");
        List<Document> pages = IntStream.range(0, 5)
                .mapToObj(i -> new Document(
                        "Spring Boot Guide — Chapter " + (i + 1) + "\n"
                                + "This page explains " + topics.get(i) + " in detail.\n"
                                + "Copyright 2026 DevDocs · page " + (i + 1),
                        Map.of("page_number", i + 1)))
                .toList();

        List<Document> cleaned = cleaner.cleanPages(pages, 10);

        assertThat(cleaned).hasSize(5);
        assertThat(cleaned.get(2).getText()).isEqualTo("This page explains proxies in detail.");
        assertThat(cleaned.get(2).getMetadata()).containsEntry("page_number", 3);
    }

    @Test
    void removesHeaderAndFooterEvenWhenPdfLayoutPadsLinesWithSpaces() {
        String pad = " ".repeat(150);   // PagePdfDocumentReader đệm dấu cách tới hết bề rộng trang
        List<String> topics = List.of("beans", "transactions", "proxies", "profiles");
        List<Document> pages = IntStream.range(0, 4)
                .mapToObj(i -> new Document(
                        "        DevDocs Sample Notes - Spring Boot Notes" + pad + "\n"
                                + "        This page explains " + topics.get(i) + " in detail." + pad + "\n"
                                + "        Spring Boot Notes | page " + (i + 1) + pad))
                .toList();

        List<Document> cleaned = cleaner.cleanPages(pages, 10);

        assertThat(cleaned).extracting(Document::getText).containsExactly(
                "This page explains beans in detail.",
                "This page explains transactions in detail.",
                "This page explains proxies in detail.",
                "This page explains profiles in detail.");
    }

    @Test
    void removesCommonPdfMarginButKeepsRelativeIndentation() {
        String pdfText = "                  Example:\n                  void run() {\n                      go();\n                  }";
        assertThat(cleaner.clean(pdfText)).isEqualTo("Example:\nvoid run() {\n    go();\n}");
    }

    @Test
    void doesNotTreatLinesAsHeaderWhenTooFewPages() {
        List<Document> pages = List.of(
                new Document("Same title\nContent A long enough to keep."),
                new Document("Same title\nContent B long enough to keep."));

        List<Document> cleaned = cleaner.cleanPages(pages, 10);

        assertThat(cleaned.getFirst().getText()).startsWith("Same title");
    }

    @Test
    void skipsPagesShorterThanMinimumAfterCleaning() {
        List<Document> pages = List.of(
                new Document("   \n\n  7  \n"),                       // trang trắng chỉ có số trang
                new Document("Hình 1"),                               // trang chỉ có ảnh + caption ngắn
                new Document("Đây là một trang có đủ nội dung để được giữ lại."));

        List<Document> cleaned = cleaner.cleanPages(pages, 30);

        assertThat(cleaned).extracting(Document::getText)
                .containsExactly("Đây là một trang có đủ nội dung để được giữ lại.");
    }
}
