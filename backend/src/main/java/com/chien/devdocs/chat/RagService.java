package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatRequest;
import com.chien.devdocs.chat.dto.ChatResponse;
import com.chien.devdocs.chat.dto.SourceDto;
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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

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

    /**
     * Phiên bản streaming của {@link #answer}: cùng các bước, nhưng câu trả lời của LLM được đẩy ra từng mẩu.
     * <p>
     * Retrieval ({@link #prepare}) chạy <b>đồng bộ</b> trước khi trả Flux: lỗi ở bước này (Ollama tắt → 503) vẫn
     * thành HTTP status bình thường, vì khi stream đã bắt đầu thì status 200 đã được gửi đi. Không có chunk nào →
     * chỉ một sự kiện {@code done}, không gọi LLM (BR-QRY-03).
     * <p>
     * Khi client ngắt kết nối, Spring MVC hủy subscription → lời gọi Ollama bị hủy theo; câu hỏi vẫn được ghi log.
     */
    public Flux<RagStreamEvent> stream(ChatRequest request) {
        Prepared p = prepare(request);
        if (!p.hasContext()) {
            return Flux.just(new RagStreamEvent.Done(notFound(p).toResponse()));
        }
        List<Document> chunks = p.context().chunks();
        List<SourceDto> candidates = IntStream.range(0, chunks.size())
                .mapToObj(i -> sourceMapper.toDto(i + 1, chunks.get(i)))
                .toList();

        StringBuilder answer = new StringBuilder();
        AtomicBoolean finished = new AtomicBoolean();
        long llmStart = System.currentTimeMillis();

        Flux<RagStreamEvent> tokens = chatClient.prompt()
                .system(systemPrompt)
                .user(u -> u.text(userPrompt)
                        .param("context", p.context().text())
                        .param("question", p.question()))
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
