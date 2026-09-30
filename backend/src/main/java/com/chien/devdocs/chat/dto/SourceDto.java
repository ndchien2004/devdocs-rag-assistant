package com.chien.devdocs.chat.dto;

/**
 * Một nguồn trích dẫn: {@code [index]} trong câu trả lời → file + trang.
 *
 * @param snippet 200 ký tự đầu của chunk
 */
public record SourceDto(
        int index,
        String documentId,
        String fileName,
        Integer pageNumber,
        String sectionTitle,
        String snippet,
        Double score
) {
}
