package com.chien.devdocs.chat;

import com.chien.devdocs.common.Topic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Nhật ký mỗi câu hỏi (BR-QRY-07) — dùng để phân tích câu hỏi nào hệ thống trả lời kém.
 */
@Entity
@Table(name = "query_logs")
public class QueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Topic topic;

    @Column(name = "top_k", nullable = false)
    private int topK;

    @Column(name = "retrieved_count", nullable = false)
    private int retrievedCount;

    @Column(name = "max_score")
    private Double maxScore;

    @Column(nullable = false)
    private boolean found;

    @Column(name = "retrieval_ms", nullable = false)
    private long retrievalMs;

    @Column(name = "llm_ms")
    private Long llmMs;

    @Column(name = "total_ms", nullable = false)
    private long totalMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QueryLog() {
    }

    public QueryLog(String question, Topic topic, int topK, int retrievedCount, Double maxScore, boolean found,
                    long retrievalMs, Long llmMs, long totalMs) {
        this.question = question;
        this.topic = topic;
        this.topK = topK;
        this.retrievedCount = retrievedCount;
        this.maxScore = maxScore;
        this.found = found;
        this.retrievalMs = retrievalMs;
        this.llmMs = llmMs;
        this.totalMs = totalMs;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getQuestion() {
        return question;
    }

    public Topic getTopic() {
        return topic;
    }

    public int getTopK() {
        return topK;
    }

    public int getRetrievedCount() {
        return retrievedCount;
    }

    public Double getMaxScore() {
        return maxScore;
    }

    public boolean isFound() {
        return found;
    }

    public long getRetrievalMs() {
        return retrievalMs;
    }

    public Long getLlmMs() {
        return llmMs;
    }

    public long getTotalMs() {
        return totalMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
