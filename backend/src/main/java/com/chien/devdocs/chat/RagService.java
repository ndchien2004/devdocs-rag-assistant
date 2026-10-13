package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatRequest;
import com.chien.devdocs.chat.dto.ChatResponse;
import com.chien.devdocs.chat.dto.SourceDto;
import com.chien.devdocs.common.exception.AiServiceUnavailableException;
import com.chien.devdocs.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;

/**
 * Phase 2 — RAG "thủ công" (mục 7.2): tự retrieve, tự ghép prompt, tự gọi LLM, tự map nguồn.
 * Viết tay toàn bộ để hiểu bản chất trước khi dùng Advisor có sẵn (Phase 4).
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final Retriever retriever;
    private final PromptBuilder promptBuilder;
    private final SourceMapper sourceMapper;
    private final ChatClient chatClient;
    private final QueryLogger queryLogger;
    private final RagProperties props;
    private final Resource systemPrompt;
    private final Resource userPrompt;

    public RagService(Retriever retriever, PromptBuilder promptBuilder, SourceMapper sourceMapper,
                      ChatClient chatClient, QueryLogger queryLogger, RagProperties props,
                      @Value("classpath:prompts/rag-system.st") Resource systemPrompt,
                      @Value("classpath:prompts/rag-user.st") Resource userPrompt) {
        this.retriever = retriever;
        this.promptBuilder = promptBuilder;
        this.sourceMapper = sourceMapper;
        this.chatClient = chatClient;
        this.queryLogger = queryLogger;
        this.props = props;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
    }

    public ChatResponse ask(ChatRequest request) {
        return answer(request).toResponse();
    }

    public RagAnswer answer(ChatRequest request) {
        Prepared p = prepare(request);
        if (!p.hasContext()) {
            return notFound(p);
        }
        long llmStart = System.currentTimeMillis();
        String answer = callLlm(p.context().text(), p.question());                          // [5]
        long llmMs = System.currentTimeMillis() - llmStart;
        log.info("LLM: {} context chunks → {} chars answer in {} ms", p.context().chunks().size(),
                answer.length(), llmMs);
        return finish(p, answer, llmMs);
    }

    /**
     * Kết quả các bước [2] → [4], dùng chung cho {@link #answer} và streaming.
     *
     * @param context {@code null} khi không có chunk nào vượt ngưỡng (BR-QRY-03)
     */
    record Prepared(ChatRequest request, String question, int topK, List<Document> hits, Double maxScore,
                    PromptBuilder.BuiltContext context, long retrievalMs, long startMs) {

        boolean hasContext() {
            return context != null;
        }
    }

    Prepared prepare(ChatRequest request) {
        long start = System.currentTimeMillis();
        String question = normalize(request.question());                                    // [2]
        int topK = request.topKOrDefault(props.topK());

        List<Document> hits = retriever.retrieve(question, request.topic(), topK,          // [3]
                props.similarityThreshold());
        long retrievalMs = System.currentTimeMillis() - start;
        Double maxScore = hits.isEmpty() ? null : hits.getFirst().getScore();
        log.info("Retrieve: topic={} topK={} → {} hits (max score {}) in {} ms",
                request.topic(), topK, hits.size(), maxScore, retrievalMs);

        PromptBuilder.BuiltContext context = hits.isEmpty()
                ? null : promptBuilder.buildContext(hits, props.maxContextTokens());       // [4]
        return new Prepared(request, question, topK, hits, maxScore, context, retrievalMs, start);
    }

    /** BR-QRY-03: không có chunk nào → trả "không tìm thấy", không gọi LLM. */
    RagAnswer notFound(Prepared p) {
        long total = System.currentTimeMillis() - p.startMs();
        saveLog(p, false, null, total);
        return new RagAnswer(ChatResponse.NOT_FOUND_ANSWER, false, List.of(), total, false, 0);
    }

    /** Bước [6] → [7] sau khi đã có câu trả lời đầy đủ của LLM. */
    RagAnswer finish(Prepared p, String answer, long llmMs) {
        boolean found = !RefusalDetector.isRefusal(answer);
        List<SourceDto> sources = found ? sourceMapper.toSources(p.context().chunks(), answer) : List.of(); // [6]
        long total = System.currentTimeMillis() - p.startMs();
        saveLog(p, found, llmMs, total);                                                     // [7]
        return new RagAnswer(answer, found, sources, total, true, p.hits().size());
    }

    private String callLlm(String context, String question) {
        try {
            String answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(u -> u.text(userPrompt)
                            .param("context", context)
                            .param("question", question))
                    .call()
                    .content();
            return answer == null ? "" : answer.strip();
        } catch (RuntimeException e) {
            if (AiServiceUnavailableException.isConnectivityProblem(e)) {
                throw new AiServiceUnavailableException(e);
            }
            throw e;
        }
    }

    static String normalize(String text) {
        return Normalizer.normalize(text.strip(), Normalizer.Form.NFC);
    }

    private void saveLog(Prepared p, boolean found, Long llmMs, long totalMs) {
        queryLogger.save(new QueryLog(p.question(), p.request().topic(), RagMode.MANUAL, p.topK(), p.hits().size(),
                p.maxScore(), found, p.retrievalMs(), llmMs, totalMs));
    }
}
