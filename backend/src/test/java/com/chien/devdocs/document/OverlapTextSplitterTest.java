package com.chien.devdocs.document;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverlapTextSplitterTest {

    /** "w0 w1 w2 ..." — mỗi từ ngắn như vậy là 1–2 token cl100k. */
    private static String words(int n) {
        return IntStream.range(0, n).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
    }

    @Test
    void consecutiveChunksShareTheirBoundaryWords() {
        List<String> chunks = new OverlapTextSplitter(20, 6).splitText(words(60));

        assertThat(chunks).hasSizeGreaterThan(2);
        for (int i = 1; i < chunks.size(); i++) {
            String[] prev = chunks.get(i - 1).split(" ");
            String firstOfNext = chunks.get(i).split(" ")[0];
            assertThat(List.of(prev)).as("chunk %d must start inside chunk %d", i, i - 1).contains(firstOfNext);
        }
        // Không mất từ nào: từ cuối cùng nằm ở chunk cuối.
        assertThat(chunks.getLast()).endsWith("w59");
    }

    @Test
    void withoutOverlapChunksDoNotRepeatWords() {
        List<String> chunks = new OverlapTextSplitter(20, 0).splitText(words(60));

        String joined = String.join(" ", chunks);
        assertThat(joined).isEqualTo(words(60));
    }

    @Test
    void neverSplitsInsideAVietnameseWord() {
        String text = "Giao dịch bị hoàn tác khi có lỗi xảy ra trong quá trình xử lý đơn hàng. ".repeat(20);
        List<String> chunks = new OverlapTextSplitter(30, 10).splitText(text);

        assertThat(chunks).allSatisfy(c -> assertThat(c).doesNotContain("�"));
        assertThat(chunks).allSatisfy(c -> assertThat(text).contains(c));
    }

    @Test
    void keepsMetadataOfTheSourcePage() {
        var page = new Document(words(80), Map.of("page_number", 7));

        List<Document> chunks = new OverlapTextSplitter(20, 5).apply(List.of(page));

        assertThat(chunks).hasSizeGreaterThan(1)
                .allSatisfy(c -> assertThat(c.getMetadata()).containsEntry("page_number", 7));
    }

    @Test
    void rejectsOverlapNotSmallerThanChunkSize() {
        assertThatThrownBy(() -> new OverlapTextSplitter(10, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
