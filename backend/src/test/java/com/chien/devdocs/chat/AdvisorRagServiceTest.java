package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatRequest;
import com.chien.devdocs.chat.dto.ChatResponse;
import com.chien.devdocs.common.Topic;
import com.chien.devdocs.config.RagProperties;
import com.chien.devdocs.support.FakeAiModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.unit.DataSize;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chạy Advisor thật của Spring AI trên ChatClient thật, với VectorStore mock và chat model giả.
 */
class AdvisorRagServiceTest {

    private VectorStore vectorStore;
    private FakeAiModels.CountingChatModel chatModel;
    private QueryLogRepository queryLogRepository;
    private AdvisorRagService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        chatModel = new FakeAiModels.CountingChatModel();
        queryLogRepository = mock(QueryLogRepository.class);
        RagProperties props = new RagProperties(5, 0.5, 3000, 1000, 500, 0, 200, 10, 30,
                DataSize.ofMegabytes(20), "./storage");
        service = new AdvisorRagService(ChatClient.builder(chatModel).build(), vectorStore, new PromptBuilder(),
                new SourceMapper(), new QueryLogger(queryLogRepository), props,
                new ClassPathResource("prompts/rag-system.st"), new ClassPathResource("prompts/rag-user.st"));
    }

    private static Document chunk(String text, int page, double score) {
        return Document.builder().text(text)
                .metadata(Map.of("document_id", "d1", "file_name", "02_Spring_Boot.pdf", "page_number", page))
                .score(score).build();
    }

    @Test
    void ragAdvisorNumbersContextLikeManualRagAndMapsCitedSources() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                chunk("REQUIRES_NEW luôn tạo transaction mới.", 5, 0.8),
                chunk("Bean scope singleton.", 3, 0.6)));
        chatModel.reset("REQUIRES_NEW luôn tạo transaction mới [1].");

        RagAnswer answer = service.answer(new ChatRequest("REQUIRES_NEW là gì?", Topic.SPRING, 3), RagMode.RAG_ADVISOR);

        assertThat(answer.found()).isTrue();
        assertThat(answer.retrievedCount()).isEqualTo(2);
        assertThat(answer.sources()).extracting(s -> s.pageNumber()).containsExactly(5);
        String prompt = chatModel.lastPrompt().getContents();
        assertThat(prompt).contains("<context>", "[1] (02_Spring_Boot.pdf — trang 5)", "[2] (02_Spring_Boot.pdf — trang 3)",
                "Câu hỏi: REQUIRES_NEW là gì?");

        ArgumentCaptor<SearchRequest> search = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(search.capture());
        assertThat(search.getValue().getTopK()).isEqualTo(3);
        assertThat(search.getValue().getSimilarityThreshold()).isEqualTo(0.5);
        assertThat(search.getValue().getFilterExpression()).isNotNull();

        ArgumentCaptor<QueryLog> log = ArgumentCaptor.forClass(QueryLog.class);
        verify(queryLogRepository).save(log.capture());
        assertThat(log.getValue().getMode()).isEqualTo(RagMode.RAG_ADVISOR);
        assertThat(log.getValue().getTopic()).isEqualTo(Topic.SPRING);
        assertThat(log.getValue().getTopK()).isEqualTo(3);
        assertThat(log.getValue().getRetrievedCount()).isEqualTo(2);
        assertThat(log.getValue().getMaxScore()).isEqualTo(0.8);
        assertThat(log.getValue().isFound()).isTrue();
        assertThat(log.getValue().getRetrievalMs()).isNull();
    }

    @Test
    void qaAdvisorUsesDefaultTemplateWithoutNumberedCitations() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                chunk("REQUIRES_NEW luôn tạo transaction mới.", 5, 0.8)));
        chatModel.reset("REQUIRES_NEW luôn tạo transaction mới.");

        RagAnswer answer = service.answer(new ChatRequest("REQUIRES_NEW là gì?", null, null), RagMode.QA_ADVISOR);

        assertThat(answer.found()).isTrue();
        assertThat(answer.sources()).hasSize(1);   // không có [n] → trả toàn bộ chunk đã dùng
        assertThat(chatModel.lastPrompt().getContents()).contains("Context information is below");
    }

    @Test
    void advisorsStillCallTheLlmWhenNothingIsRetrieved() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        chatModel.reset("I can't answer that question.");

        RagAnswer rag = service.answer(new ChatRequest("Thời tiết hôm nay?", null, null), RagMode.RAG_ADVISOR);
        RagAnswer qa = service.answer(new ChatRequest("Thời tiết hôm nay?", null, null), RagMode.QA_ADVISOR);

        // Khác với RAG thủ công (BR-QRY-03): advisor vẫn gọi LLM dù không có context.
        assertThat(chatModel.calls()).isEqualTo(2);
        assertThat(rag.found()).isFalse();
        assertThat(qa.found()).isFalse();
        assertThat(rag.sources()).isEmpty();
        // Câu từ chối gốc giữ lại cho báo cáo E5, nhưng API trả câu chuẩn giống RAG thủ công.
        assertThat(rag.answer()).isEqualTo("I can't answer that question.");
        assertThat(rag.toResponse().answer()).isEqualTo(ChatResponse.NOT_FOUND_ANSWER);
        verify(queryLogRepository, times(2)).save(any(QueryLog.class));
    }

    @Test
    void manualModeIsNotHandledHere() {
        assertThatThrownBy(() -> service.answer(new ChatRequest("x", null, null), RagMode.MANUAL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
