package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatRequest;
import com.chien.devdocs.chat.dto.ChatResponse;
import com.chien.devdocs.chat.dto.ConversationDtos;
import com.chien.devdocs.chat.dto.SourceDto;
import com.chien.devdocs.common.Topic;
import com.chien.devdocs.common.exception.AiServiceUnavailableException;
import com.chien.devdocs.common.exception.ConversationNotFoundException;
import com.chien.devdocs.common.exception.InvalidDocumentScopeException;
import com.chien.devdocs.document.DocumentStatus;
import com.chien.devdocs.document.SourceDocument;
import com.chien.devdocs.document.SourceDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationServiceTest {
    private ConversationRepository conversations;
    private SourceDocumentRepository documents;
    private RagService rag;
    private ConversationService service;
    private SourceDocument indexed;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        documents = mock(SourceDocumentRepository.class);
        rag = mock(RagService.class);
        // Transaction manager giả: TransactionTemplate chỉ việc chạy callback.
        service = new ConversationService(conversations, documents, rag,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
        indexed = new SourceDocument(UUID.randomUUID(), "a.pdf", "application/pdf", 10, "checksum", Topic.JAVA, "a.pdf");
        indexed.markIndexed(1, 1);
        when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsWithAllCurrentlyIndexedDocumentsByDefault() {
        when(documents.search(null, DocumentStatus.INDEXED)).thenReturn(List.of(indexed));
        when(documents.findAllById(Set.of(indexed.getId()))).thenReturn(List.of(indexed));

        ConversationDtos.Response created = service.create(null);

        assertThat(created.documentIds()).containsExactly(indexed.getId());
    }

    @Test
    void rejectsScopeContainingUnindexedDocuments() {
        when(documents.findAllById(Set.of(indexed.getId()))).thenReturn(List.of(indexed));
        assertThatThrownBy(() -> service.create(new ConversationDtos.CreateRequest(Set.of(UUID.randomUUID()))))
                .isInstanceOf(InvalidDocumentScopeException.class);
    }

    @Test
    void persistsUserAndGroundedAssistantMessages() {
        Conversation conversation = new Conversation(Set.of(indexed.getId()));
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(conversations.findWithLockById(conversation.getId())).thenReturn(Optional.of(conversation));
        SourceDto source = new SourceDto(1, indexed.getId().toString(), "a.pdf", 4, null, "snippet", .9);
        when(rag.answer(any(ChatRequest.class), any(), any())).thenReturn(
                new RagAnswer("Có trong tài liệu [1].", true, List.of(source), 10, true, 1));

        ConversationDtos.Response response = service.ask(conversation.getId(),
                new ConversationDtos.AskRequest("Câu hỏi?", Topic.JAVA));

        assertThat(response.messages()).hasSize(2);
        assertThat(response.messages().get(0).role()).isEqualTo("USER");
        assertThat(response.messages().get(1).found()).isTrue();
        assertThat(response.messages().get(1).sources()).containsExactly(source);
        verify(rag).answer(any(ChatRequest.class), any(), any());
        verify(conversations).save(conversation);
    }

    @Test
    void askPassesScopeAndOnlyTheLastTwelveMessagesAsHistory() {
        Conversation conversation = new Conversation(Set.of(indexed.getId()));
        for (int i = 0; i < 7; i++) {
            conversation.addMessage(new ConversationMessage("USER", "hỏi " + i, null, List.of()));
            conversation.addMessage(new ConversationMessage("ASSISTANT", "đáp " + i, true, List.of()));
        }
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(conversations.findWithLockById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(rag.answer(any(ChatRequest.class), any(), any())).thenReturn(
                new RagAnswer("Có [1].", true, List.of(), 10, true, 1));

        ConversationDtos.Response response = service.ask(conversation.getId(),
                new ConversationDtos.AskRequest("  hỏi tiếp  ", null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ConversationMessage>> history = ArgumentCaptor.forClass(List.class);
        verify(rag).answer(any(ChatRequest.class), eq(List.of(indexed.getId())), history.capture());
        assertThat(history.getValue()).hasSize(12);
        assertThat(history.getValue().getFirst().getContent()).isEqualTo("hỏi 1");
        assertThat(history.getValue().getLast().getContent()).isEqualTo("đáp 6");
        assertThat(response.messages()).hasSize(16);
        assertThat(response.messages().get(14).content()).isEqualTo("hỏi tiếp");
    }

    @Test
    void llmFailurePersistsNothing() {
        Conversation conversation = new Conversation(Set.of(indexed.getId()));
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(rag.answer(any(ChatRequest.class), any(), any()))
                .thenThrow(new AiServiceUnavailableException(new ConnectException("refused")));

        assertThatThrownBy(() -> service.ask(conversation.getId(), new ConversationDtos.AskRequest("Câu hỏi?", null)))
                .isInstanceOf(AiServiceUnavailableException.class);

        assertThat(conversation.getMessages()).isEmpty();
        verify(conversations, never()).findWithLockById(any());
        verify(conversations, never()).save(any());
    }

    @Test
    void conversationDeletedWhileTheLlmWasAnsweringIs404() {
        Conversation conversation = new Conversation(Set.of(indexed.getId()));
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(conversations.findWithLockById(conversation.getId())).thenReturn(Optional.empty());
        when(rag.answer(any(ChatRequest.class), any(), any())).thenReturn(
                new RagAnswer("Có [1].", true, List.of(), 10, true, 1));

        assertThatThrownBy(() -> service.ask(conversation.getId(), new ConversationDtos.AskRequest("Câu hỏi?", null)))
                .isInstanceOf(ConversationNotFoundException.class);
        verify(conversations, never()).save(any());
    }

    @Test
    void unknownConversationIs404WithoutCallingRag() {
        UUID id = UUID.randomUUID();
        when(conversations.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ask(id, new ConversationDtos.AskRequest("Câu hỏi?", null)))
                .isInstanceOf(ConversationNotFoundException.class);
        verify(rag, never()).answer(any(ChatRequest.class), any(), any());
    }

    @Test
    void scopeCanBeSetToEmpty() {
        Conversation conversation = new Conversation(Set.of(indexed.getId()));
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        ConversationDtos.Response response = service.setScope(conversation.getId(), Set.of());
        assertThat(response.documentIds()).isEmpty();
    }

    // ------------------------------------------------------------------ streaming

    private Conversation conversationInScope() {
        Conversation conversation = new Conversation(Set.of(indexed.getId()));
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(conversations.findWithLockById(conversation.getId())).thenReturn(Optional.of(conversation));
        return conversation;
    }

    @Test
    void streamingSavesBothMessagesBeforeEmittingDone() {
        Conversation conversation = conversationInScope();
        SourceDto source = new SourceDto(1, indexed.getId().toString(), "a.pdf", 4, null, "snippet", .9);
        when(rag.stream(any(ChatRequest.class), any(), any())).thenReturn(Flux.just(
                new RagStreamEvent.Sources(List.of(source)),
                new RagStreamEvent.Token("Có [1]."),
                new RagStreamEvent.Done(new ChatResponse("Có [1].", true, List.of(source), 5))));

        StepVerifier.create(service.askStream(conversation.getId(),
                        new ConversationDtos.AskRequest("Hỏi gì?", null, 3)))
                .expectNextCount(2)
                // Tới lúc phát done, hai tin nhắn đã nằm trong hội thoại.
                .assertNext(e -> assertThat(conversation.getMessages()).hasSize(2))
                .verifyComplete();

        assertThat(conversation.getMessages()).extracting(ConversationMessage::getRole).containsExactly("USER", "ASSISTANT");
        assertThat(conversation.getMessages().get(1).getSources()).containsExactly(source);
        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(rag).stream(request.capture(), eq(List.of(indexed.getId())), any());
        assertThat(request.getValue().topK()).isEqualTo(3);
    }

    @Test
    void streamThatFailsMidAnswerSavesNothing() {
        Conversation conversation = conversationInScope();
        when(rag.stream(any(ChatRequest.class), any(), any())).thenReturn(Flux.just(
                new RagStreamEvent.Token("Đang"),
                new RagStreamEvent.Failed(new AiServiceUnavailableException(new ConnectException("x")).getBody())));

        StepVerifier.create(service.askStream(conversation.getId(), new ConversationDtos.AskRequest("Hỏi", null)))
                .expectNextCount(2)
                .verifyComplete();

        assertThat(conversation.getMessages()).isEmpty();
        verify(conversations, never()).save(any());
    }

    @Test
    void conversationDeletedWhileStreamingEndsWithA404ErrorEvent() {
        Conversation conversation = conversationInScope();
        when(conversations.findWithLockById(conversation.getId())).thenReturn(Optional.empty());
        when(rag.stream(any(ChatRequest.class), any(), any())).thenReturn(Flux.just(
                new RagStreamEvent.Done(new ChatResponse("Có [1].", true, List.of(), 5))));

        StepVerifier.create(service.askStream(conversation.getId(), new ConversationDtos.AskRequest("Hỏi", null)))
                .assertNext(e -> assertThat(((RagStreamEvent.Failed) e).problem().getStatus()).isEqualTo(404))
                .verifyComplete();
    }

    @Test
    void streamingUnknownConversationFailsBeforeAnyStreamStarts() {
        UUID missing = UUID.randomUUID();
        when(conversations.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.askStream(missing, new ConversationDtos.AskRequest("Hỏi", null)))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void refusalIsStoredAsTheStandardNotFoundAnswerNotTheModelsRawText() {
        Conversation conversation = conversationInScope();
        when(rag.answer(any(ChatRequest.class), any(), any())).thenReturn(
                new RagAnswer("I can't answer that.", false, List.of(), 10, true, 1));

        service.ask(conversation.getId(), new ConversationDtos.AskRequest("Hỏi", null));

        assertThat(conversation.getMessages().get(1).getContent()).isEqualTo(ChatResponse.NOT_FOUND_ANSWER);
    }
}
