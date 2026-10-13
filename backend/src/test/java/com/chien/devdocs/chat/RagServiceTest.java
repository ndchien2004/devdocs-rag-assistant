package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatRequest;
import com.chien.devdocs.chat.dto.ChatResponse;
import com.chien.devdocs.common.Topic;
import com.chien.devdocs.common.exception.AiServiceUnavailableException;
import com.chien.devdocs.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.ResourceAccessException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagServiceTest {

    private Retriever retriever;
    private ChatClient chatClient;
    private QueryLogRepository queryLogRepository;
    private RagService service;

    @BeforeEach
    void setUp() {
        retriever = mock(Retriever.class);
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        queryLogRepository = mock(QueryLogRepository.class);
        RagProperties props = new RagProperties(5, 0.5, 3000, 500, 0, 200, 10, 30,
                DataSize.ofMegabytes(20), "./storage");
        service = new RagService(retriever, new PromptBuilder(), new SourceMapper(), chatClient,
                new QueryLogger(queryLogRepository), props,
                new ByteArrayResource("system".getBytes()), new ByteArrayResource("{context} {question}".getBytes()));
    }

    @SuppressWarnings("unchecked")
    private void llmAnswers(String answer) {
        when(chatClient.prompt().system(any(org.springframework.core.io.Resource.class))
                .user(any(Consumer.class)).call().content()).thenReturn(answer);
    }

    @SuppressWarnings("unchecked")
    private void llmStreams(Flux<String> tokens) {
        when(chatClient.prompt().system(any(org.springframework.core.io.Resource.class))
                .user(any(Consumer.class)).stream().content()).thenReturn(tokens);
    }

    private static Document hit(String text, int page, double score) {
        return Document.builder().text(text)
                .metadata(Map.of("document_id", "d1", "file_name", "02_Spring_Boot.pdf", "page_number", page))
                .score(score).build();
    }

    @Test
    void noHitsReturnsNotFoundWithoutCallingLlm() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of());

        ChatResponse response = service.ask(new ChatRequest("Thời tiết Hà Nội hôm nay thế nào?", null, null));

        assertThat(response.found()).isFalse();
        assertThat(response.answer()).isEqualTo(ChatResponse.NOT_FOUND_ANSWER);
        assertThat(response.sources()).isEmpty();
        verifyNoInteractions(chatClient);

        ArgumentCaptor<QueryLog> log = ArgumentCaptor.forClass(QueryLog.class);
        verify(queryLogRepository).save(log.capture());
        assertThat(log.getValue().isFound()).isFalse();
        assertThat(log.getValue().getMode()).isEqualTo(RagMode.MANUAL);
        assertThat(log.getValue().getRetrievedCount()).isZero();
        assertThat(log.getValue().getLlmMs()).isNull();
    }

    @Test
    void answersWithOnlyCitedSources() {
        when(retriever.retrieve(any(), eq(Topic.SPRING), eq(3), eq(0.5))).thenReturn(List.of(
                hit("REQUIRED tham gia transaction hiện có.", 5, 0.82),
                hit("REQUIRES_NEW tạo transaction mới.", 5, 0.77),
                hit("Bean scope singleton.", 3, 0.55)));
        llmAnswers("REQUIRED tham gia transaction hiện có [1]. REQUIRES_NEW luôn tạo mới [2].");

        ChatResponse response = service.ask(new ChatRequest("REQUIRED vs REQUIRES_NEW?", Topic.SPRING, 3));

        assertThat(response.found()).isTrue();
        assertThat(response.answer()).contains("[1]").contains("[2]");
        assertThat(response.sources()).extracting(s -> s.index()).containsExactly(1, 2);
        assertThat(response.sources().getFirst().score()).isEqualTo(0.82);

        ArgumentCaptor<QueryLog> log = ArgumentCaptor.forClass(QueryLog.class);
        verify(queryLogRepository).save(log.capture());
        assertThat(log.getValue().isFound()).isTrue();
        assertThat(log.getValue().getRetrievedCount()).isEqualTo(3);
        assertThat(log.getValue().getMaxScore()).isEqualTo(0.82);
        assertThat(log.getValue().getTopK()).isEqualTo(3);
        assertThat(log.getValue().getTopic()).isEqualTo(Topic.SPRING);
    }

    @Test
    void llmSayingNotFoundGivesFoundFalse() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("x", 1, 0.6)));
        llmAnswers("Mình không tìm thấy nội dung liên quan trong tài liệu đã nạp.");

        ChatResponse response = service.ask(new ChatRequest("Câu hỏi lạc đề", null, null));

        assertThat(response.found()).isFalse();
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void refusalWordedDifferentlyIsNormalizedInResponseButKeptInRagAnswer() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("x", 1, 0.6)));
        String refusal = "Xin lỗi, tài liệu không có thông tin về câu hỏi này.";
        llmAnswers(refusal);

        RagAnswer answer = service.answer(new ChatRequest("Câu hỏi lạc đề", null, null));

        assertThat(answer.answer()).isEqualTo(refusal);
        assertThat(answer.toResponse().answer()).isEqualTo(ChatResponse.NOT_FOUND_ANSWER);
    }

    @Test
    void failingToSaveQueryLogDoesNotBreakTheAnswer() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        when(queryLogRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThat(service.ask(new ChatRequest("x", null, null)).found()).isFalse();
    }

    @Test
    void questionIsTrimmedAndNormalizedToNfcBeforeRetrieval() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        String nfd = Normalizer.normalize("  Giao dịch bị hoàn tác?  ", Normalizer.Form.NFD);

        service.ask(new ChatRequest(nfd, null, null));

        verify(retriever).retrieve(eq("Giao dịch bị hoàn tác?"), eq(null), eq(5), eq(0.5));
    }

    @Test
    @SuppressWarnings("unchecked")
    void llmConnectionFailureBecomes503() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("x", 1, 0.9)));
        when(chatClient.prompt().system(any(org.springframework.core.io.Resource.class))
                .user(any(Consumer.class)).call().content())
                .thenThrow(new ResourceAccessException("I/O error", new ConnectException("refused")));

        assertThatThrownBy(() -> service.ask(new ChatRequest("Hỏi gì đó", null, null)))
                .isInstanceOf(AiServiceUnavailableException.class);
    }

    @Test
    void recognizesRefusalVariants() {
        assertThat(RefusalDetector.isRefusal("Mình không tìm thấy nội dung liên quan trong tài liệu đã nạp.")).isTrue();
        assertThat(RefusalDetector.isRefusal("I'm sorry, but I can’t answer that question.")).isTrue();
        // Câu từ chối thật của qwen2.5:3b với QuestionAnswerAdvisor (E5, Q28):
        assertThat(RefusalDetector.isRefusal(
                "Xin lỗi, nhưng thông tin về đội vô địch World Cup 2022 không được tìm thấy trong context.")).isTrue();
        assertThat(RefusalDetector.isRefusal(
                "The context provided does not contain information about why to use StringBuilder.")).isTrue();
        assertThat(RefusalDetector.isRefusal("  ")).isTrue();
        assertThat(RefusalDetector.isRefusal("REQUIRED là mặc định [1].")).isFalse();
    }

    @Test
    void answerReportsWhetherLlmWasCalled() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        assertThat(service.answer(new ChatRequest("x", null, null)).llmCalled()).isFalse();

        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("y", 1, .9)));
        llmAnswers("Trả lời [1].");
        RagAnswer answer = service.answer(new ChatRequest("x", null, null));
        assertThat(answer.llmCalled()).isTrue();
        assertThat(answer.retrievedCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ streaming

    @Test
    void streamWithoutHitsSendsOnlyDoneAndNeverCallsLlm() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of());

        StepVerifier.create(service.stream(new ChatRequest("Thời tiết hôm nay?", null, null)))
                .assertNext(e -> {
                    ChatResponse done = ((RagStreamEvent.Done) e).response();
                    assertThat(done.found()).isFalse();
                    assertThat(done.answer()).isEqualTo(ChatResponse.NOT_FOUND_ANSWER);
                })
                .verifyComplete();
        verifyNoInteractions(chatClient);
        verify(queryLogRepository).save(any(QueryLog.class));
    }

    @Test
    void streamSendsCandidateSourcesThenTokensThenDoneWithOnlyCitedSources() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(
                hit("REQUIRED tham gia transaction hiện có.", 5, 0.82),
                hit("REQUIRES_NEW tạo transaction mới.", 5, 0.77),
                hit("Bean scope singleton.", 3, 0.55)));
        llmStreams(Flux.just("REQUIRED tham gia", " transaction [1].\n", "REQUIRES_NEW tạo mới [2]."));

        StepVerifier.create(service.stream(new ChatRequest("REQUIRED vs REQUIRES_NEW?", null, null)))
                .assertNext(e -> assertThat(((RagStreamEvent.Sources) e).sources())
                        .extracting(s -> s.index()).containsExactly(1, 2, 3))
                .expectNext(new RagStreamEvent.Token("REQUIRED tham gia"),
                        new RagStreamEvent.Token(" transaction [1].\n"),
                        new RagStreamEvent.Token("REQUIRES_NEW tạo mới [2]."))
                .assertNext(e -> {
                    ChatResponse done = ((RagStreamEvent.Done) e).response();
                    assertThat(done.found()).isTrue();
                    assertThat(done.answer()).isEqualTo("REQUIRED tham gia transaction [1].\nREQUIRES_NEW tạo mới [2].");
                    assertThat(done.sources()).extracting(s -> s.index()).containsExactly(1, 2);
                })
                .verifyComplete();

        ArgumentCaptor<QueryLog> log = ArgumentCaptor.forClass(QueryLog.class);
        verify(queryLogRepository).save(log.capture());
        assertThat(log.getValue().isFound()).isTrue();
        assertThat(log.getValue().getLlmMs()).isNotNull();
    }

    @Test
    void streamedRefusalEndsWithStandardNotFoundAnswer() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("x", 1, 0.6)));
        llmStreams(Flux.just("Mình không tìm thấy", " nội dung liên quan."));

        StepVerifier.create(service.stream(new ChatRequest("Lạc đề", null, null)))
                .expectNextCount(3)
                .assertNext(e -> {
                    ChatResponse done = ((RagStreamEvent.Done) e).response();
                    assertThat(done.found()).isFalse();
                    assertThat(done.answer()).isEqualTo(ChatResponse.NOT_FOUND_ANSWER);
                    assertThat(done.sources()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void llmConnectionLostMidStreamEndsWithError503AndIsLogged() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("x", 1, 0.9)));
        llmStreams(Flux.concat(Flux.just("Đang trả lời"),
                Flux.error(new ResourceAccessException("I/O error", new ConnectException("refused")))));

        StepVerifier.create(service.stream(new ChatRequest("Hỏi gì đó", null, null)))
                .expectNextCount(2)
                .assertNext(e -> assertThat(((RagStreamEvent.Failed) e).problem().getStatus()).isEqualTo(503))
                .verifyComplete();

        ArgumentCaptor<QueryLog> log = ArgumentCaptor.forClass(QueryLog.class);
        verify(queryLogRepository).save(log.capture());
        assertThat(log.getValue().isFound()).isFalse();
    }

    @Test
    void unexpectedLlmErrorBecomesGeneric500WithoutLeakingDetails() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("x", 1, 0.9)));
        llmStreams(Flux.error(new IllegalStateException("secret internal detail")));

        StepVerifier.create(service.stream(new ChatRequest("Hỏi gì đó", null, null)))
                .expectNextCount(1)
                .assertNext(e -> {
                    var problem = ((RagStreamEvent.Failed) e).problem();
                    assertThat(problem.getStatus()).isEqualTo(500);
                    assertThat(problem.getDetail()).doesNotContain("secret");
                })
                .verifyComplete();
    }

    @Test
    void cancellingTheStreamCancelsTheLlmCallAndStillLogsTheQuestionOnce() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("x", 1, 0.9)));
        AtomicBoolean llmCancelled = new AtomicBoolean();
        llmStreams(Flux.concat(Flux.just("Đang"), Flux.<String>never()).doOnCancel(() -> llmCancelled.set(true)));

        StepVerifier.create(service.stream(new ChatRequest("Hỏi gì đó", null, null)))
                .expectNextCount(2)
                .thenCancel()
                .verify();

        assertThat(llmCancelled).isTrue();
        ArgumentCaptor<QueryLog> log = ArgumentCaptor.forClass(QueryLog.class);
        verify(queryLogRepository).save(log.capture());
        assertThat(log.getValue().isFound()).isFalse();
    }

    @Test
    void retrievalFailureIsThrownBeforeAnyStreamStartsSoItBecomesA503Status() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble()))
                .thenThrow(new AiServiceUnavailableException(new ConnectException("refused")));

        assertThatThrownBy(() -> service.stream(new ChatRequest("Hỏi gì đó", null, null)))
                .isInstanceOf(AiServiceUnavailableException.class);
    }
}
