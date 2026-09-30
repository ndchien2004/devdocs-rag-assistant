package com.chien.devdocs.document;

import com.chien.devdocs.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bước [5] → [8] của luồng ingestion: đọc file → làm sạch → cắt chunk → gắn metadata.
 * Tách riêng để Phase 4 chạy lại đúng pipeline này với chunk size / overlap khác mà không đụng tới dữ liệu chính.
 */
@Component
public class ChunkingPipeline {

    private final DocumentLoader loader;
    private final TextCleaner cleaner;
    private final ChunkSplitter splitter;
    private final FileStorage storage;
    private final RagProperties props;

    public ChunkingPipeline(DocumentLoader loader, TextCleaner cleaner, ChunkSplitter splitter, FileStorage storage,
                            RagProperties props) {
        this.loader = loader;
        this.cleaner = cleaner;
        this.splitter = splitter;
        this.storage = storage;
        this.props = props;
    }

    /**
     * @param usablePages số trang còn lại sau khi làm sạch (0 = không trích xuất được văn bản)
     */
    public record Result(List<Document> chunks, int pageCount, int usablePages) {
    }

    public Result chunk(SourceDocument doc) {
        return chunk(doc, props.chunkSize(), props.chunkOverlap());
    }

    public Result chunk(SourceDocument doc, int chunkSize, int overlap) {
        return chunk(doc, chunkSize, overlap, overlap > 0 ? ChunkSplitter.Strategy.WINDOW : ChunkSplitter.Strategy.TOKEN);
    }

    public Result chunk(SourceDocument doc, int chunkSize, int overlap, ChunkSplitter.Strategy strategy) {
        FileType type = FileType.fromFileName(doc.getFileName()).orElseThrow();
        DocumentLoader.LoadedFile loaded = loader.load(storage.resolve(doc.getStoragePath()), type);    // [5]
        List<Document> pages = cleaner.cleanPages(loaded.pages(), props.minPageChars());               // [6]
        if (pages.isEmpty()) {
            return new Result(List.of(), loaded.pageCount(), 0);
        }
        List<Document> chunks = withMetadata(splitter.split(pages, chunkSize, overlap, strategy), doc); // [7] [8]
        return new Result(chunks, loaded.pageCount(), pages.size());
    }

    /** BR-ING-06: 5 metadata bắt buộc cho mỗi chunk. Metadata thừa của reader bị bỏ đi. */
    static List<Document> withMetadata(List<Document> chunks, SourceDocument doc) {
        List<Document> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> md = new HashMap<>();
            md.put(ChunkMetadata.DOCUMENT_ID, doc.getId().toString());
            md.put(ChunkMetadata.FILE_NAME, doc.getFileName());
            md.put(ChunkMetadata.PAGE_NUMBER, pageNumber(chunk));
            md.put(ChunkMetadata.TOPIC, doc.getTopic().name());
            md.put(ChunkMetadata.CHUNK_INDEX, i);
            Object section = chunk.getMetadata().get(DocumentLoader.SECTION_TITLE);
            if (section != null) {
                md.put(ChunkMetadata.SECTION_TITLE, section);
            }
            result.add(Document.builder().text(chunk.getText()).metadata(md).build());
        }
        return result;
    }

    private static int pageNumber(Document chunk) {
        Object value = chunk.getMetadata().get(DocumentLoader.PAGE_NUMBER);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }
}
