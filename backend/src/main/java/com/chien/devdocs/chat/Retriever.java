package com.chien.devdocs.chat;

import com.chien.devdocs.common.Topic;
import com.chien.devdocs.common.exception.AiServiceUnavailableException;
import com.chien.devdocs.document.ChunkMetadata;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Bước retrieve của RAG: embedding câu hỏi rồi tìm top-K chunk gần nhất trong pgvector.
 * Dùng chung cho RagService (hỏi đáp) và EvaluationService (đo chất lượng retrieval).
 */
@Component
public class Retriever {

    private final VectorStore vectorStore;

    public Retriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * @return chunk có similarity ≥ threshold, sắp xếp theo score giảm dần
     */
    public List<Document> retrieve(String question, Topic topic, int topK, double similarityThreshold) {
        return retrieve(vectorStore, question, topic, List.of(), topK, similarityThreshold);
    }

    /** Tìm trên một VectorStore cụ thể (Phase 4: so sánh nhiều bảng vector với cấu hình chunk khác nhau). */
    public List<Document> retrieve(VectorStore store, String question, Topic topic, int topK,
                                   double similarityThreshold) {
        return retrieve(store, question, topic, List.of(), topK, similarityThreshold);
    }

    public List<Document> retrieve(String question, Topic topic, List<UUID> documentIds, int topK,
                                   double similarityThreshold) {
        return retrieve(vectorStore, question, topic, documentIds, topK, similarityThreshold);
    }

    private List<Document> retrieve(VectorStore store, String question, Topic topic, List<UUID> documentIds,
                                    int topK, double similarityThreshold) {
        SearchRequest.Builder search = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(similarityThreshold);
        // Filter được dựng bằng builder từ enum/UUID → không bao giờ ghép chuỗi người dùng vào filter (NFR-06).
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op filter = null;
        if (!documentIds.isEmpty()) {
            // Ép về List<Object> để gọi đúng overload in(String, List<Object>) thay vì bản varargs.
            filter = filters.in(ChunkMetadata.DOCUMENT_ID,
                    documentIds.stream().<Object>map(UUID::toString).toList());
        }
        if (topic != null) {
            FilterExpressionBuilder.Op byTopic = filters.eq(ChunkMetadata.TOPIC, topic.name());
            filter = filter == null ? byTopic : filters.and(filter, byTopic);
        }
        if (filter != null) {
            search.filterExpression(filter.build());
        }
        try {
            return store.similaritySearch(search.build()).stream()
                    .sorted(Comparator.comparing(Document::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } catch (RuntimeException e) {
            if (AiServiceUnavailableException.isConnectivityProblem(e)) {
                throw new AiServiceUnavailableException(e);
            }
            throw e;
        }
    }
}
