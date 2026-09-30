package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.SourceDto;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SourceMapperTest {

    private final SourceMapper mapper = new SourceMapper();

    private static List<Document> chunks(int n) {
        return java.util.stream.IntStream.rangeClosed(1, n)
                .mapToObj(i -> Document.builder().text("Nội dung chunk " + i)
                        .metadata(Map.of("document_id", "doc-" + i, "file_name", "f" + i + ".pdf", "page_number", i * 10))
                        .score(1.0 - i / 10.0).build())
                .toList();
    }

    @Test
    void returnsOnlyCitedSources() {
        List<SourceDto> sources = mapper.toSources(chunks(4), "Ý thứ nhất [1][3]. Ý thứ hai [3].");

        assertThat(sources).extracting(SourceDto::index).containsExactly(1, 3);
        assertThat(sources.getFirst()).isEqualTo(
                new SourceDto(1, "doc-1", "f1.pdf", 10, null, "Nội dung chunk 1", 0.9));
    }

    @Test
    void understandsCommaSeparatedCitations() {
        assertThat(mapper.toSources(chunks(4), "Xem [2, 4]."))
                .extracting(SourceDto::index).containsExactly(2, 4);
    }

    @Test
    void returnsAllSourcesWhenNoCitationCanBeParsed() {
        assertThat(mapper.toSources(chunks(3), "Câu trả lời không có trích dẫn."))
                .extracting(SourceDto::index).containsExactly(1, 2, 3);
    }

    @Test
    void ignoresCitationNumbersOutsideTheContext() {
        assertThat(mapper.toSources(chunks(2), "Bịa số [7] và dùng thật [2]."))
                .extracting(SourceDto::index).containsExactly(2);
        // chỉ có số bịa → coi như không parse được → trả toàn bộ
        assertThat(mapper.toSources(chunks(2), "Chỉ có [9]."))
                .extracting(SourceDto::index).containsExactly(1, 2);
    }

    @Test
    void doesNotTreatArrayIndexingInCodeAsCitationWhenOutOfRange() {
        assertThat(SourceMapper.parseCitations("int x = arr[0]; y = arr[100];", 5)).isEmpty();
    }

    @Test
    void snippetIsFirst200CharactersFlattened() {
        String longText = "a\n\n b ".repeat(100);
        String snippet = SourceMapper.snippet(longText);

        assertThat(snippet).startsWith("a b a b").endsWith("…").doesNotContain("\n");
        assertThat(snippet).hasSize(SourceMapper.SNIPPET_LENGTH + 1);
    }
}
