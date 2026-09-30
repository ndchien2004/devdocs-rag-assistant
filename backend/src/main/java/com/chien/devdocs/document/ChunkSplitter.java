package com.chien.devdocs.document;

import com.chien.devdocs.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cắt trang thành chunk theo token (BR-ING-05). TokenTextSplitter copy metadata của trang sang từng chunk,
 * nên {@code page_number} được giữ nguyên.
 */
@Component
public class ChunkSplitter {

    private final RagProperties props;

    public ChunkSplitter(RagProperties props) {
        this.props = props;
    }

    public List<Document> split(List<Document> pages) {
        return split(pages, props.chunkSize());
    }

    public List<Document> split(List<Document> pages, int chunkSize) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(props.minChunkSizeChars())
                .withMinChunkLengthToEmbed(props.minChunkLengthToEmbed())
                .withKeepSeparator(true)
                .build();
        return splitter.apply(pages);
    }
}
