package com.chien.devdocs.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Model AI giả để integration test chạy không cần Ollama.
 */
public final class FakeAiModels {

    private FakeAiModels() {
    }

    /**
     * Embedding "bag of words": mỗi từ được băm vào 1 trong 1024 chiều, rồi chuẩn hóa độ dài vector.
     * Hai đoạn dùng chung nhiều từ → cosine cao; không chung từ nào → cosine ≈ 0. Đủ để kiểm tra luồng retrieval.
     */
    public static class BagOfWordsEmbeddingModel implements EmbeddingModel {

        public static final int DIMENSIONS = 1024;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                embeddings.add(new Embedding(vector(request.getInstructions().get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        static float[] vector(String text) {
            float[] v = new float[DIMENSIONS];
            String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
            for (String word : normalized.split("[^\\p{L}\\p{N}_]+")) {
                if (word.length() > 1) {
                    v[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1;
                }
            }
            double norm = 0;
            for (float x : v) {
                norm += x * x;
            }
            if (norm > 0) {
                float n = (float) Math.sqrt(norm);
                for (int i = 0; i < v.length; i++) {
                    v[i] /= n;
                }
            }
            return v;
        }
    }

    /** Chat model trả lời cố định và đếm số lần bị gọi (để kiểm tra BR-QRY-03: không có chunk → không gọi LLM). */
    public static class CountingChatModel implements ChatModel {

        private final AtomicInteger calls = new AtomicInteger();
        private volatile String answer = "Câu trả lời dựa trên tài liệu [1].";
        private volatile Prompt lastPrompt;

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            lastPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
        }

        public int calls() {
            return calls.get();
        }

        public Prompt lastPrompt() {
            return lastPrompt;
        }

        public void reset(String nextAnswer) {
            calls.set(0);
            lastPrompt = null;
            answer = nextAnswer;
        }
    }
}
