package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatRequest;
import com.chien.devdocs.chat.dto.ChatResponse;
import com.chien.devdocs.chat.dto.SourceDto;
import com.chien.devdocs.common.Topic;
import com.chien.devdocs.common.exception.AiServiceUnavailableException;
import com.chien.devdocs.common.exception.GlobalExceptionHandler;
import com.chien.devdocs.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * Phase 2 — RAG "thủ công" (mục 7.2): tự retrieve, tự ghép prompt, tự gọi LLM, tự map nguồn.
 * Viết tay toàn bộ để hiểu bản chất trước khi dùng Advisor có sẵn (Phase 4).
 * <p>
 * Chia thành {@link #prepare} → gọi LLM ({@link #answer} hoặc {@link #stream}) → {@link #finish}, để hỏi đáp thường,
 * streaming và hội thoại đã lưu đi qua đúng cùng các bước.
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

    /** Hỏi đáp một lượt trên mọi tài liệu (không giới hạn phạm vi, không có lịch sử). */
    public RagAnswer answer(ChatRequest request) {
        return answer(request, null, List.of());
    }

    /**
     * @param documentIds phạm vi tài liệu của hội thoại: {@code null} = tìm trên mọi tài liệu;
     *                    danh sách rỗng = không chọn tài liệu nào → trả "không tìm thấy", không retrieve
     */
    public RagAnswer answer(ChatRequest request, List<UUID> documentIds, List<ConversationMessage> history) {
        Prepared p = prepare(request, documentIds, history);
        if (!p.hasContext()) {
            return notFound(p);
        }
        long llmStart = System.currentTimeMillis();
        String answer = callLlm(p);                                                          // [5]
        long llmMs = System.currentTimeMillis() - llmStart;
        log.info("LLM: {} context chunks → {} chars answer in {} ms", p.context().chunks().size(),
                answer.length(), llmMs);
        return finish(p, answer, llmMs);
    }

    /** Streaming một lượt trên mọi tài liệu (không giới hạn phạm vi, không có lịch sử). */
    public Flux<RagStreamEvent> stream(ChatRequest request) {
        return stream(request, null, List.of());
    }

    /**
     * Phiên bản streaming của {@link #answer}: cùng các bước, nhưng câu trả lời của LLM được đẩy ra từng mẩu.
     * <p>
     * Retrieval ({@link #prepare}) chạy <b>đồng bộ</b> trước khi trả Flux: lỗi ở bước này (Ollama tắt → 503) vẫn
     * thành HTTP status bình thường, vì khi stream đã bắt đầu thì status 200 đã được gửi đi. Không có chunk nào →
     * chỉ một sự kiện {@code done}, không gọi LLM (BR-QRY-03).
     * <p>
     * Khi client ngắt kết nối, Spring MVC hủy subscription → lời gọi Ollama bị hủy theo; câu hỏi vẫn được ghi log.
     */
    public Flux<RagStreamEvent> stream(ChatRequest request, List<UUID> documentIds,
                                       List<ConversationMessage> history) {
        Prepared p = prepare(request, documentIds, history);
        if (!p.hasContext()) {
            return Flux.just(new RagStreamEvent.Done(notFound(p).toResponse()));
        }
        List<Document> chunks = p.context().chunks();
        List<SourceDto> candidates = allSources(chunks);

        StringBuilder answer = new StringBuilder();
        AtomicBoolean finished = new AtomicBoolean();
        long llmStart = System.currentTimeMillis();

        Flux<RagStreamEvent> tokens = prompt(p)
                .stream()
                .content()
                .filter(Objects::nonNull)
                .doOnNext(answer::append)
                .map(RagStreamEvent.Token::new);

        Mono<RagStreamEvent> done = Mono.fromCallable(() -> {
            finished.set(true);
            long llmMs = System.currentTimeMillis() - llmStart;
            log.info("LLM (stream): {} context chunks → {} chars answer in {} ms", chunks.size(),
                    answer.length(), llmMs);
            return new RagStreamEvent.Done(finish(p, answer.toString().strip(), llmMs).toResponse());
        });

        return Flux.concat(Mono.just(new RagStreamEvent.Sources(candidates)), tokens, done)
                .onErrorResume(e -> {
                    if (finished.compareAndSet(false, true)) {
                        saveIncomplete(p, llmStart);
                    }
                    return Mono.just(new RagStreamEvent.Failed(problemFor(e)));
                })
                .doOnCancel(() -> {
                    if (finished.compareAndSet(false, true)) {
                        log.info("LLM (stream): cancelled by client after {} chars", answer.length());
                        saveIncomplete(p, llmStart);
                    }
                });
    }

    /**
     * Kết quả các bước [2] → [4], dùng chung cho {@link #answer} và {@link #stream}.
     *
     * @param context {@code null} khi không có chunk nào vượt ngưỡng hoặc phạm vi rỗng (BR-QRY-03)
     */
    record Prepared(ChatRequest request, String question, int topK, List<Document> hits, Double maxScore,
                    PromptBuilder.BuiltContext context, String history, long retrievalMs, long startMs) {

        boolean hasContext() {
            return context != null;
        }
    }

    Prepared prepare(ChatRequest request, List<UUID> documentIds, List<ConversationMessage> history) {
        long start = System.currentTimeMillis();
        String question = normalize(request.question());                                    // [2]
        int topK = request.topKOrDefault(props.topK());

        if (documentIds != null && documentIds.isEmpty()) {
            return new Prepared(request, question, topK, List.of(), null, null, NO_HISTORY, 0, start);
        }
        List<Document> hits = retrieve(question, request.topic(), documentIds, topK);       // [3]
        String previousQuestion = hits.isEmpty() ? previousUserQuestion(history) : null;
        if (previousQuestion != null) {
            // Câu hỏi nối tiếp ("Còn cái kia thì sao?") tự nó không đủ nghĩa để tìm → thử lại kèm câu hỏi liền trước.
            // Chỉ thử khi lần tìm đầu không ra gì, để câu hỏi đổi chủ đề không bị kéo về chủ đề cũ.
            hits = retrieve(previousQuestion + "\n" + question, request.topic(), documentIds, topK);
        }
        long retrievalMs = System.currentTimeMillis() - start;
        Double maxScore = hits.isEmpty() ? null : hits.getFirst().getScore();
        log.info("Retrieve: topic={} topK={} → {} hits (max score {}) in {} ms",
                request.topic(), topK, hits.size(), maxScore, retrievalMs);

        PromptBuilder.BuiltContext context = hits.isEmpty()
                ? null : promptBuilder.buildContext(hits, props.maxContextTokens());       // [4]
        return new Prepared(request, question, topK, hits, maxScore, context,
                hits.isEmpty() ? NO_HISTORY : formatHistory(history), retrievalMs, start);
    }

    /** BR-QRY-03: không có chunk nào (hoặc phạm vi rỗng) → trả "không tìm thấy", không gọi LLM. */
    RagAnswer notFound(Prepared p) {
        long total = System.currentTimeMillis() - p.startMs();
        saveLog(p, false, null, total);
        return new RagAnswer(ChatResponse.NOT_FOUND_ANSWER, false, List.of(), total, false, 0);
    }

    /**
     * Bước [6] → [7] sau khi đã có câu trả lời đầy đủ của LLM.
     * <ul>
     *   <li>Câu từ chối → "không tìm thấy" (BR-QRY-03).</li>
     *   <li>Có trích dẫn hợp lệ → chỉ trả nguồn được trích (BR-QRY-02).</li>
     *   <li>Không có trích dẫn hợp lệ nào (model nhỏ đôi khi quên [n]) → vẫn giữ câu trả lời, trả <b>mọi</b> đoạn đã
     *       đưa cho model để người dùng đối chiếu; giao diện gắn cảnh báo "không có trích dẫn".
     *       {@code query_logs.found = false} để đo được tỉ lệ câu trả lời không có căn cứ.</li>
     * </ul>
     */
    RagAnswer finish(Prepared p, String answer, long llmMs) {
        List<Document> chunks = p.context().chunks();
        boolean refusal = RefusalDetector.isRefusal(answer);
        // Số ngoài khoảng (LLM bịa, hoặc chỉ số mảng như args[0]) không tính là trích dẫn.
        List<SourceDto> cited = refusal ? List.of() : sourceMapper.toSources(chunks, answer);  // [6]
        List<SourceDto> sources = cited;
        if (refusal) {
            log.info("Answer treated as refusal → returning 'not found' ({} chars)", answer.length());
        } else if (cited.isEmpty()) {
            log.info("Answer has no valid citation → kept, returned with all {} context chunks as sources ({} chars)",
                    chunks.size(), answer.length());
            sources = allSources(chunks);
        }
        if (refusal || cited.isEmpty()) {
            log.debug("Ungrounded answer: {}", answer);
        }
        long total = System.currentTimeMillis() - p.startMs();
        saveLog(p, !refusal && !cited.isEmpty(), llmMs, total);                              // [7]
        return new RagAnswer(answer, !refusal, sources, total, true, p.hits().size());
    }

    /** Mọi chunk trong CONTEXT, đánh số [1]..[n] đúng như trong prompt. */
    private List<SourceDto> allSources(List<Document> chunks) {
        return IntStream.range(0, chunks.size()).mapToObj(i -> sourceMapper.toDto(i + 1, chunks.get(i))).toList();
    }

    private ChatClient.ChatClientRequestSpec prompt(Prepared p) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(u -> u.text(userPrompt)
                        .param("context", p.context().text())
                        .param("history", p.history())
                        .param("question", p.question()));
    }

    private String callLlm(Prepared p) {
        try {
            String answer = prompt(p).call().content();
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

    /** Stream bị hủy hoặc lỗi giữa chừng: vẫn ghi log (BR-QRY-07), coi như không có câu trả lời. */
    private void saveIncomplete(Prepared p, long llmStart) {
        long now = System.currentTimeMillis();
        saveLog(p, false, now - llmStart, now - p.startMs());
    }

    private static ProblemDetail problemFor(Throwable e) {
        if (AiServiceUnavailableException.isConnectivityProblem(e)) {
            log.warn("LLM (stream) unavailable: {}", e.getMessage());
            return new AiServiceUnavailableException(e).getBody();
        }
        log.error("LLM (stream) failed", e);
        return GlobalExceptionHandler.internalError();
    }

    static String normalize(String text) {
        return Normalizer.normalize(text.strip(), Normalizer.Form.NFC);
    }

    private void saveLog(Prepared p, boolean found, Long llmMs, long totalMs) {
        queryLogger.save(new QueryLog(p.question(), p.request().topic(), RagMode.MANUAL, p.topK(), p.hits().size(),
                p.maxScore(), found, p.retrievalMs(), llmMs, totalMs));
    }
}
