package com.chien.devdocs.playground;

import com.chien.devdocs.common.VectorMath;
import com.chien.devdocs.playground.dto.EmbeddingResponse;
import com.chien.devdocs.playground.dto.SimilarityResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * Phase 0 — làm quen Spring AI: gọi chat model và embedding model trực tiếp, chưa có RAG.
 */
@Service
public class PlaygroundService {

    private static final int PREVIEW_SIZE = 5;

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

    public PlaygroundService(ChatClient chatClient, EmbeddingModel embeddingModel) {
        this.chatClient = chatClient;
        this.embeddingModel = embeddingModel;
    }

    public String hello(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }

    public EmbeddingResponse embed(String text) {
        float[] vector = embeddingModel.embed(text);
        float[] head = Arrays.copyOf(vector, Math.min(PREVIEW_SIZE, vector.length));
        return new EmbeddingResponse(text, vector.length, head);
    }

    public SimilarityResponse similarity(String a, String b) {
        float[] va = embeddingModel.embed(a);
        float[] vb = embeddingModel.embed(b);
        return new SimilarityResponse(a, b, VectorMath.cosineSimilarity(va, vb));
    }
}
