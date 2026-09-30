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
    /** Chuỗi nhận diện câu "không tìm thấy" do LLM trả về theo system prompt (BR-QRY-03). */
    private static final String NOT_FOUND_MARKER = "không tìm thấy nội dung liên quan";

    private final Retriever retriever;
    private final PromptBuilder promptBuilder;
    private final SourceMapper sourceMapper;
    private final ChatClient chatClient;
    private final QueryLogRepository queryLogRepository;
    private final RagProperties props;
    private final Resource systemPrompt;
    private final Resource userPrompt;

    public RagService(Retriever retriever, PromptBuilder promptBuilder, SourceMapper sourceMapper,
                      ChatClient chatClient, QueryLogRepository queryLogRepository, RagProperties props,
                      @Value("classpath:prompts/rag-system.st") Resource systemPrompt,
                      @Value("classpath:prompts/rag-user.st") Resource userPrompt) {
        this.retriever = retriever;
        this.promptBuilder = promptBuilder;
        this.sourceMapper = sourceMapper;
        this.chatClient = chatClient;
        this.queryLogRepository = queryLogRepository;
        this.props = props;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
    }

    public ChatResponse ask(ChatRequest request) {
        long start = System.currentTimeMillis();
        String question = normalize(request.question());                                    // [2]
        int topK = request.topKOrDefault(props.topK());

        long retrievalStart = System.currentTimeMillis();
        List<Document> hits = retriever.retrieve(question, request.topic(), topK,          // [3]
                props.similarityThreshold());
        long retrievalMs = System.currentTimeMillis() - retrievalStart;
        Double maxScore = hits.isEmpty() ? null : hits.getFirst().getScore();
        log.info("Retrieve: topic={} topK={} → {} hits (max score {}) in {} ms",
                request.topic(), topK, hits.size(), maxScore, retrievalMs);

        if (hits.isEmpty()) {                                                               // BR-QRY-03
            long total = System.currentTimeMillis() - start;
            saveLog(question, request, topK, 0, null, false, retrievalMs, null, total);
            return ChatResponse.notFound(total);
        }

        PromptBuilder.BuiltContext context = promptBuilder.buildContext(hits, props.maxContextTokens()); // [4]

        long llmStart = System.currentTimeMillis();
        String answer = callLlm(context.text(), question);                                  // [5]
        long llmMs = System.currentTimeMillis() - llmStart;
        log.info("LLM: {} context chunks → {} chars answer in {} ms", context.chunks().size(),
                answer.length(), llmMs);

        boolean found = !isNotFoundAnswer(answer);
        List<SourceDto> sources = found ? sourceMapper.toSources(context.chunks(), answer) : List.of(); // [6]

        long total = System.currentTimeMillis() - start;
        saveLog(question, request, topK, hits.size(), maxScore, found, retrievalMs, llmMs, total);  // [7]
        return new ChatResponse(found ? answer : ChatResponse.NOT_FOUND_ANSWER, found, sources, total);
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

    static boolean isNotFoundAnswer(String answer) {
        return answer.isBlank() || normalize(answer).toLowerCase().contains(NOT_FOUND_MARKER);
    }

    static String normalize(String text) {
        return Normalizer.normalize(text.strip(), Normalizer.Form.NFC);
    }

    private void saveLog(String question, ChatRequest request, int topK, int retrievedCount, Double maxScore,
                         boolean found, long retrievalMs, Long llmMs, long totalMs) {
        try {
            queryLogRepository.save(new QueryLog(question, request.topic(), topK, retrievedCount, maxScore, found,
                    retrievalMs, llmMs, totalMs));
        } catch (RuntimeException e) {
            // Ghi log thất bại không được làm hỏng câu trả lời.
            log.warn("Could not save query log: {}", e.getMessage());
        }
    }
}
