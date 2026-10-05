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

import java.net.ConnectException;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RagServiceTest {

    private Retriever retriever;
    private ChatClient chatClient;
    private QueryLogRepository queryLogRepository;
    private RagService service;
    private final ByteArrayResource systemPrompt = new ByteArrayResource("system".getBytes());

    @BeforeEach
    void setUp() {
        retriever = mock(Retriever.class);
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        queryLogRepository = mock(QueryLogRepository.class);
        service = newService(new PromptBuilder(), 1000);
    }

    private RagService newService(PromptBuilder promptBuilder, int maxHistoryTokens) {
        RagProperties props = new RagProperties(5, 0.5, 3000, maxHistoryTokens, 500, 0, 200, 10, 30,
                DataSize.ofMegabytes(20), "./storage");
        return new RagService(retriever, promptBuilder, new SourceMapper(), chatClient,
                queryLogRepository, props,
                systemPrompt, new ByteArrayResource("{context} {history} {question}".getBytes()));
    }

    /** Giá trị điền vào một biến của rag-user.st ở lần gọi LLM gần nhất. */
    @SuppressWarnings("unchecked")
    private Object userPromptParam(String name) {
        ArgumentCaptor<Consumer<ChatClient.PromptUserSpec>> consumer = ArgumentCaptor.forClass(Consumer.class);
        verify(chatClient.prompt().system(systemPrompt), atLeastOnce()).user(consumer.capture());
        ChatClient.PromptUserSpec spec = mock(ChatClient.PromptUserSpec.class, RETURNS_SELF);
        consumer.getValue().accept(spec);
        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(spec).param(eq(name), value.capture());
        return value.getValue();
    }

    private static ConversationMessage user(String text) {
        return new ConversationMessage("USER", text, null, List.of());
    }

    private static ConversationMessage assistant(String text) {
        return new ConversationMessage("ASSISTANT", text, true, List.of());
    }

    @SuppressWarnings("unchecked")
    private void llmAnswers(String answer) {
        when(chatClient.prompt().system(any(org.springframework.core.io.Resource.class))
                .user(any(Consumer.class)).call().content()).thenReturn(answer);
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
    void answerWithoutAValidCitationIsRefused() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("facts", 1, 0.8)));
        llmAnswers("Câu trả lời tự tin nhưng không trích dẫn.");

        ChatResponse response = service.ask(new ChatRequest("Hỏi", null, null));

        assertThat(response.found()).isFalse();
        assertThat(response.answer()).isEqualTo(ChatResponse.NOT_FOUND_ANSWER);
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void bracketNumbersOutsideTheContextDoNotDiscardACitedAnswer() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("main(String[] args)", 1, 0.8)));
        llmAnswers("Tham số đầu tiên nằm ở args[0] của main [1]. Một ý khác [7].");

        ChatResponse response = service.ask(new ChatRequest("Tham số dòng lệnh nằm ở đâu?", null, null));

        assertThat(response.found()).isTrue();
        assertThat(response.answer()).contains("args[0]");
        assertThat(response.sources()).extracting(s -> s.index()).containsExactly(1);
    }

    @Test
    void refusalThatStillCitesReturnsNoSources() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("x", 1, 0.6)));
        llmAnswers("Mình không tìm thấy nội dung liên quan trong tài liệu đã nạp [1].");

        ChatResponse response = service.ask(new ChatRequest("Câu hỏi lạc đề", null, null));

        assertThat(response.found()).isFalse();
        assertThat(response.answer()).isEqualTo(ChatResponse.NOT_FOUND_ANSWER);
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void followUpIsRetrievedWithTheBareQuestionWhenThatFindsChunks() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("N+1", 1, 0.9)));
        llmAnswers("Trả lời [1].");

        service.answer(new ChatRequest("N+1 query là gì?", null, null), null,
                List.of(user("REQUIRED là gì?"), assistant("REQUIRED là mặc định [1].")));

        // Đổi chủ đề giữa hội thoại: không được kéo câu hỏi cũ vào truy vấn tìm kiếm.
        verify(retriever).retrieve(eq("N+1 query là gì?"), eq(null), eq(5), eq(0.5));
        verifyNoMoreInteractions(retriever);
    }

    @Test
    void followUpFallsBackToThePreviousQuestionOnlyWhenTheBareQuestionFindsNothing() {
        when(retriever.retrieve(eq("REQUIRED là gì?\nCòn cái kia thì sao?"), any(), anyInt(), anyDouble()))
                .thenReturn(List.of(hit("REQUIRES_NEW tạo transaction mới.", 5, 0.7)));
        llmAnswers("REQUIRES_NEW luôn tạo mới [1].");

        RagAnswer answer = service.answer(new ChatRequest("Còn cái kia thì sao?", null, null), null,
                List.of(user("Bean scope là gì?"), assistant("Singleton là mặc định [1]."),
                        user("REQUIRED là gì?"), assistant("REQUIRED là mặc định [1].")));

        assertThat(answer.found()).isTrue();
        verify(retriever).retrieve(eq("Còn cái kia thì sao?"), eq(null), eq(5), eq(0.5));
        verify(retriever, times(2)).retrieve(any(), any(), anyInt(), anyDouble());
    }

    @Test
    void fallbackRetrievalStaysInsideTheDocumentScope() {
        List<UUID> scope = List.of(UUID.randomUUID());

        RagAnswer answer = service.answer(new ChatRequest("Còn cái kia thì sao?", null, null), scope,
                List.of(user("REQUIRED là gì?"), assistant("REQUIRED là mặc định [1].")));

        assertThat(answer.found()).isFalse();
        assertThat(answer.llmCalled()).isFalse();
        verify(retriever).retrieve(eq("Còn cái kia thì sao?"), eq(null), eq(scope), eq(5), eq(0.5));
        verify(retriever).retrieve(eq("REQUIRED là gì?\nCòn cái kia thì sao?"), eq(null), eq(scope), eq(5), eq(0.5));
        verifyNoMoreInteractions(retriever);
        verifyNoInteractions(chatClient);
    }

    @Test
    void historyInThePromptCarriesNoCitationNumbersFromEarlierTurns() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("x", 1, 0.9)));
        llmAnswers("Trả lời [1].");

        service.answer(new ChatRequest("Còn REQUIRES_NEW?", null, null), null,
                List.of(user("REQUIRED là gì?"), assistant("REQUIRED tham gia transaction hiện có [1][2].")));

        assertThat(userPromptParam("history"))
                .isEqualTo("Người dùng: REQUIRED là gì?\nTrợ lý: REQUIRED tham gia transaction hiện có.");
    }

    @Test
    void historyOverTheTokenBudgetDropsTheOldestMessagesFirst() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("x", 1, 0.9)));
        llmAnswers("Trả lời [1].");
        List<ConversationMessage> history = List.of(user("câu hỏi cũ"), assistant("trả lời cũ"),
                user("câu hỏi mới"), assistant("trả lời mới"));
        String newestTwo = "Người dùng: câu hỏi mới\nTrợ lý: trả lời mới";

        // Bộ đếm = số ký tự, ngân sách vừa đủ hai tin nhắn mới nhất.
        newService(new PromptBuilder(String::length), newestTwo.length())
                .answer(new ChatRequest("Hỏi tiếp", null, null), null, history);

        assertThat(userPromptParam("history")).isEqualTo(newestTwo);
    }

    @Test
    void historyIsLeftOutWhenTheBudgetIsZero() {
        when(retriever.retrieve(any(), any(), anyInt(), anyDouble())).thenReturn(List.of(hit("x", 1, 0.9)));
        llmAnswers("Trả lời [1].");

        newService(new PromptBuilder(String::length), 0).answer(new ChatRequest("Hỏi tiếp", null, null), null,
                List.of(user("câu hỏi cũ"), assistant("trả lời cũ")));

        assertThat(userPromptParam("history")).isEqualTo(RagService.NO_HISTORY);
    }

    @Test
    void explicitEmptyDocumentScopeDoesNotCallRetrieverOrLlm() {
        RagAnswer response = service.answer(new ChatRequest("Hỏi", null, null), List.of(), List.of());
        assertThat(response.found()).isFalse();
        assertThat(response.llmCalled()).isFalse();
        verifyNoInteractions(retriever, chatClient);
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
}
