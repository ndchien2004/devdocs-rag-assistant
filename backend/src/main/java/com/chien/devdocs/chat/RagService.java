package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatRequest;
import com.chien.devdocs.chat.dto.ChatResponse;
import com.chien.devdocs.chat.dto.SourceDto;
import com.chien.devdocs.common.Topic;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 2 — RAG "thủ công" (mục 7.2): tự retrieve, tự ghép prompt, tự gọi LLM, tự map nguồn.
 * Viết tay toàn bộ để hiểu bản chất trước khi dùng Advisor có sẵn (Phase 4).
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    /** Giá trị điền vào biến {@code history} của rag-user.st khi không có lịch sử hội thoại. */
    static final String NO_HISTORY = "(chưa có lịch sử hội thoại)";

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
        return answer(request).toResponse();
    }

    /** Hỏi đáp một lượt trên mọi tài liệu (không giới hạn phạm vi, không có lịch sử). */
    public RagAnswer answer(ChatRequest request) {
        return answer(request, null, List.of());
    }

    /**
     * @param documentIds phạm vi tài liệu của hội thoại: {@code null} = tìm trên mọi tài liệu;
     *                    danh sách rỗng = không chọn tài liệu nào → trả "không tìm thấy", không retrieve
     */
    public RagAnswer answer(ChatRequest request, List<UUID> documentIds, List<ConversationMessage> history) {
        long start = System.currentTimeMillis();
        String question = normalize(request.question());                                    // [2]
        int topK = request.topKOrDefault(props.topK());

        if (documentIds != null && documentIds.isEmpty()) {
            long total = System.currentTimeMillis() - start;
            saveLog(question, request, topK, 0, null, false, 0, null, total);
            return new RagAnswer(ChatResponse.NOT_FOUND_ANSWER, false, List.of(), total, false, 0);
        }
        long retrievalStart = System.currentTimeMillis();
        List<Document> hits = retrieve(question, request.topic(), documentIds, topK);       // [3]
        String previousQuestion = hits.isEmpty() ? previousUserQuestion(history) : null;
        if (previousQuestion != null) {
            // Câu hỏi nối tiếp ("Còn cái kia thì sao?") tự nó không đủ nghĩa để tìm → thử lại kèm câu hỏi liền trước.
            // Chỉ thử khi lần tìm đầu không ra gì, để câu hỏi đổi chủ đề không bị kéo về chủ đề cũ.
            hits = retrieve(previousQuestion + "\n" + question, request.topic(), documentIds, topK);
        }
        long retrievalMs = System.currentTimeMillis() - retrievalStart;
        Double maxScore = hits.isEmpty() ? null : hits.getFirst().getScore();
        log.info("Retrieve: topic={} topK={} → {} hits (max score {}) in {} ms",
                request.topic(), topK, hits.size(), maxScore, retrievalMs);

        if (hits.isEmpty()) {                                                               // BR-QRY-03
            long total = System.currentTimeMillis() - start;
            saveLog(question, request, topK, 0, null, false, retrievalMs, null, total);
            return new RagAnswer(ChatResponse.NOT_FOUND_ANSWER, false, List.of(), total, false, 0);
        }

        PromptBuilder.BuiltContext context = promptBuilder.buildContext(hits, props.maxContextTokens()); // [4]

        long llmStart = System.currentTimeMillis();
        String answer = callLlm(context.text(), formatHistory(history), question);          // [5]
        long llmMs = System.currentTimeMillis() - llmStart;
        log.info("LLM: {} context chunks → {} chars answer in {} ms", context.chunks().size(),
                answer.length(), llmMs);

        // Phải có ít nhất một trích dẫn hợp lệ; số ngoài khoảng (LLM bịa, hoặc chỉ số mảng như args[0]) bị bỏ qua.
        List<SourceDto> cited = sourceMapper.toSources(context.chunks(), answer);           // [6]
        boolean found = !RefusalDetector.isRefusal(answer) && !cited.isEmpty();
        List<SourceDto> sources = found ? cited : List.of();
        if (!found) {
            // Câu trả lời của LLM bị thay bằng "không tìm thấy" — ghi lại để chẩn đoán được (bật DEBUG khi cần).
            log.debug("Answer discarded (valid citations: {}): {}", cited.size(), answer);
        }

        long total = System.currentTimeMillis() - start;
        saveLog(question, request, topK, hits.size(), maxScore, found, retrievalMs, llmMs, total);  // [7]
        return new RagAnswer(found ? answer : ChatResponse.NOT_FOUND_ANSWER, found, sources, total, true,
                hits.size());
    }

    private String callLlm(String context, String history, String question) {
        try {
            String answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(u -> u.text(userPrompt)
                            .param("context", context)
                            .param("history", history)
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

    private List<Document> retrieve(String query, Topic topic, List<UUID> documentIds, int topK) {
        return documentIds == null
                ? retriever.retrieve(query, topic, topK, props.similarityThreshold())
                : retriever.retrieve(query, topic, documentIds, topK, props.similarityThreshold());
    }

    /**
     * Lịch sử đưa vào prompt: tối đa 12 tin nhắn gần nhất, bỏ dần tin cũ nhất cho tới khi vừa
     * {@code max-history-tokens} (CONTEXT đã có ngân sách riêng, lịch sử không được lấn chỗ).
     */
    private String formatHistory(List<ConversationMessage> messages) {
        if (messages == null || messages.isEmpty()) return NO_HISTORY;
        List<String> lines = new ArrayList<>(messages.stream().skip(Math.max(0, messages.size() - 12L))
                .map(RagService::historyLine).toList());
        while (!lines.isEmpty() && promptBuilder.countTokens(String.join("\n", lines)) > props.maxHistoryTokens()) {
            lines.removeFirst();
        }
        return lines.isEmpty() ? NO_HISTORY : String.join("\n", lines);
    }

    private static String historyLine(ConversationMessage message) {
        boolean user = "USER".equals(message.getRole());
        // Số [n] trong câu trả lời cũ trỏ tới CONTEXT của lượt đó, không phải lượt này → gỡ đi.
        String content = user ? message.getContent() : SourceMapper.stripCitations(message.getContent());
        return (user ? "Người dùng" : "Trợ lý") + ": " + PromptInjectionGuard.sanitize(content);
    }

    /** Câu hỏi gần nhất của người dùng trong lịch sử, hoặc {@code null} nếu chưa có. */
    private static String previousUserQuestion(List<ConversationMessage> history) {
        if (history == null) return null;
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("USER".equals(history.get(i).getRole())) return history.get(i).getContent();
        }
        return null;
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
