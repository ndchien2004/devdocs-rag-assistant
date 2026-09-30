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
        RagProperties props = new RagProperties(5, 0.5, 3000, 500, 200, 10, 30,
                DataSize.ofMegabytes(20), "./storage");
        service = new RagService(retriever, new PromptBuilder(), new SourceMapper(), chatClient,
                queryLogRepository, props,
                new ByteArrayResource("system".getBytes()), new ByteArrayResource("{context} {question}".getBytes()));
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
    void recognizesNotFoundAnswerVariants() {
        assertThat(RagService.isNotFoundAnswer("Mình không tìm thấy nội dung liên quan trong tài liệu đã nạp.")).isTrue();
        assertThat(RagService.isNotFoundAnswer("  ")).isTrue();
        assertThat(RagService.isNotFoundAnswer("REQUIRED là mặc định [1].")).isFalse();
    }
}
