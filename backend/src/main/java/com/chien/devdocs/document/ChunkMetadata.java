package com.chien.devdocs.document;

/**
 * Các key metadata bắt buộc của mỗi chunk trong {@code vector_store} (BR-ING-06).
 */
public final class ChunkMetadata {

    public static final String DOCUMENT_ID = "document_id";
    public static final String FILE_NAME = "file_name";
    public static final String PAGE_NUMBER = "page_number";
    public static final String TOPIC = "topic";
    public static final String CHUNK_INDEX = "chunk_index";
    /** Tùy chọn — chỉ có với Markdown. */
    public static final String SECTION_TITLE = "section_title";

    private ChunkMetadata() {
    }
}
