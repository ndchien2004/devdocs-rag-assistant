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
        return retrieve(vectorStore, question, topic, topK, similarityThreshold);
    }

    /** Tìm trên một VectorStore cụ thể (Phase 4: so sánh nhiều bảng vector với cấu hình chunk khác nhau). */
    public List<Document> retrieve(VectorStore store, String question, Topic topic, int topK,
                                   double similarityThreshold) {
        SearchRequest.Builder search = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(similarityThreshold);
        if (topic != null) {
            // Filter được dựng bằng builder từ enum → không bao giờ ghép chuỗi người dùng vào filter (NFR-06).
            search.filterExpression(new FilterExpressionBuilder().eq(ChunkMetadata.TOPIC, topic.name()).build());
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
