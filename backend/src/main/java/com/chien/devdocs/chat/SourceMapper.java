package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.SourceDto;
import com.chien.devdocs.document.ChunkMetadata;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Map chunk → {@link SourceDto} (bước [6] luồng query, BR-QRY-02).
 * Chỉ trả về nguồn thực sự được trích dẫn trong câu trả lời; không parse được trích dẫn hợp lệ nào thì trả toàn bộ.
 */
@Component
public class SourceMapper {

    static final int SNIPPET_LENGTH = 200;

    /** [1], [2][3], [1, 3], [1,2,4] */
    private static final Pattern CITATION_GROUP = Pattern.compile("\\[(\\d+(?:\\s*,\\s*\\d+)*)]");

    public List<SourceDto> toSources(List<Document> chunks, String answer) {
        TreeSet<Integer> cited = parseCitations(answer, chunks.size());
        List<SourceDto> sources = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            int index = i + 1;
            if (cited.isEmpty() || cited.contains(index)) {
                sources.add(toDto(index, chunks.get(i)));
            }
        }
        return sources;
    }

    /** Số trích dẫn nằm trong [1, max]; số ngoài khoảng (LLM bịa số) bị bỏ qua. */
    static TreeSet<Integer> parseCitations(String answer, int max) {
        TreeSet<Integer> result = new TreeSet<>();
        if (answer == null) {
            return result;
        }
        Matcher m = CITATION_GROUP.matcher(answer);
        while (m.find()) {
            for (String n : m.group(1).split(",")) {
                try {
                    int value = Integer.parseInt(n.strip());
                    if (value >= 1 && value <= max) {
                        result.add(value);
                    }
                } catch (NumberFormatException ignored) {
                    // số quá lớn — bỏ qua
                }
            }
        }
        return result;
    }

    public SourceDto toDto(int index, Document chunk) {
        Map<String, Object> md = chunk.getMetadata();
        return new SourceDto(
                index,
                (String) md.get(ChunkMetadata.DOCUMENT_ID),
                (String) md.get(ChunkMetadata.FILE_NAME),
                toInteger(md.get(ChunkMetadata.PAGE_NUMBER)),
                (String) md.get(ChunkMetadata.SECTION_TITLE),
                snippet(chunk.getText()),
                chunk.getScore());
    }

    static String snippet(String text) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return flat.length() <= SNIPPET_LENGTH ? flat : flat.substring(0, SNIPPET_LENGTH) + "…";
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return value == null ? null : Integer.valueOf(value.toString());
    }
}
