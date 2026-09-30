package com.chien.devdocs.document;

import com.chien.devdocs.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cắt trang thành chunk theo token (BR-ING-05). Splitter copy metadata của trang sang từng chunk,
 * nên {@code page_number} được giữ nguyên.
 * <ul>
 *   <li>overlap = 0: {@link TokenTextSplitter} của Spring AI (baseline).</li>
 *   <li>overlap &gt; 0: {@link OverlapTextSplitter} tự viết (thí nghiệm E4).</li>
 * </ul>
 */
@Component
public class ChunkSplitter {

    private final RagProperties props;

    public ChunkSplitter(RagProperties props) {
        this.props = props;
    }

    public List<Document> split(List<Document> pages) {
        return split(pages, props.chunkSize(), props.chunkOverlap());
    }

    public enum Strategy {
        /** TokenTextSplitter của Spring AI — cắt theo token, ưu tiên điểm dừng câu, không overlap. */
        TOKEN,
        /** OverlapTextSplitter tự viết — cửa sổ trượt theo từ, có thể overlap. */
        WINDOW
    }

    public List<Document> split(List<Document> pages, int chunkSize, int overlap) {
        return split(pages, chunkSize, overlap, overlap > 0 ? Strategy.WINDOW : Strategy.TOKEN);
    }

    public List<Document> split(List<Document> pages, int chunkSize, int overlap, Strategy strategy) {
        if (strategy == Strategy.TOKEN && overlap > 0) {
            throw new IllegalArgumentException("TokenTextSplitter does not support overlap");
        }
        TextSplitter splitter = strategy == Strategy.WINDOW
                ? new OverlapTextSplitter(chunkSize, overlap)
                : TokenTextSplitter.builder()
                        .withChunkSize(chunkSize)
                        .withMinChunkSizeChars(props.minChunkSizeChars())
                        .withMinChunkLengthToEmbed(props.minChunkLengthToEmbed())
                        .withKeepSeparator(true)
                        .build();
        return splitter.apply(pages);
    }
}
