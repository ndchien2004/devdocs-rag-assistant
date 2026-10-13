package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatRequest;
import com.chien.devdocs.chat.dto.ChatResponse;
import com.chien.devdocs.chat.dto.ConversationDtos;
import com.chien.devdocs.common.Topic;
import com.chien.devdocs.common.exception.ApiException;
import com.chien.devdocs.common.exception.ConversationNotFoundException;
import com.chien.devdocs.common.exception.GlobalExceptionHandler;
import com.chien.devdocs.common.exception.InvalidDocumentScopeException;
import com.chien.devdocs.document.DocumentStatus;
import com.chien.devdocs.document.SourceDocument;
import com.chien.devdocs.document.SourceDocumentRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConversationService {
    private final ConversationRepository conversations;
    private final SourceDocumentRepository documents;
    private final RagService ragService;
    private final TransactionTemplate transactions;

    public ConversationService(ConversationRepository conversations, SourceDocumentRepository documents,
                               RagService ragService, TransactionTemplate transactions) {
        this.conversations = conversations; this.documents = documents; this.ragService = ragService;
        this.transactions = transactions;
    }

    @Transactional
    public ConversationDtos.Response create(ConversationDtos.CreateRequest request) {
        Set<UUID> scope = request == null || request.documentIds() == null
                ? documents.search(null, DocumentStatus.INDEXED).stream().map(SourceDocument::getId).collect(Collectors.toSet())
                : request.documentIds();
        validateScope(scope);
        return ConversationDtos.Response.from(conversations.save(new Conversation(scope)));
    }

    @Transactional
    public List<ConversationDtos.Summary> list() {
        return conversations.findAllByOrderByUpdatedAtDesc().stream().map(ConversationDtos.Summary::from).toList();
    }

    @Transactional
    public ConversationDtos.Response get(UUID id) { return ConversationDtos.Response.from(require(id)); }

    @Transactional
    public ConversationDtos.Response rename(UUID id, String title) {
        Conversation conversation = require(id); conversation.rename(title);
        return ConversationDtos.Response.from(conversations.save(conversation));
    }

    @Transactional
    public ConversationDtos.Response setScope(UUID id, Set<UUID> documentIds) {
        Conversation conversation = require(id);
        Set<UUID> scope = documentIds == null ? Set.of() : documentIds;
        validateScope(scope); conversation.setDocumentIds(scope);
        return ConversationDtos.Response.from(conversations.save(conversation));
    }

    @Transactional
    public void delete(UUID id) { conversations.delete(require(id)); }

    /**
     * Không đặt {@code @Transactional} trên cả luồng: gọi LLM có thể mất hàng phút, không được giữ kết nối DB
     * suốt thời gian đó. Đọc phạm vi + lịch sử và ghi hai tin nhắn mới là hai transaction ngắn riêng.
     */
    public ConversationDtos.Response ask(UUID id, ConversationDtos.AskRequest request) {
        AskContext context = loadContext(id);
        // LLM lỗi → ném exception ở đây, chưa có gì được ghi.
        var answer = ragService.answer(chatRequest(request), context.documentIds(), context.history());
        return saveTurn(id, request, answer.toResponse());
    }

    /**
     * Như {@link #ask} nhưng trả lời dạng streaming ({@link RagStreamEvent}). Hai tin nhắn được ghi <b>trước</b> khi
     * phát sự kiện {@code done}, nên client tải lại hội thoại ngay sau {@code done} là thấy đủ.
     * Stream lỗi hoặc bị hủy giữa chừng thì không ghi gì — giống {@link #ask} khi LLM lỗi.
     * <p>
     * Hội thoại không tồn tại / retrieval lỗi → ném exception ngay (HTTP 404 / 503), chưa mở stream.
     */
    public Flux<RagStreamEvent> askStream(UUID id, ConversationDtos.AskRequest request) {
        AskContext context = loadContext(id);
        return ragService.stream(chatRequest(request), context.documentIds(), context.history())
                .concatMap(event -> event instanceof RagStreamEvent.Done done
                        // JPA là blocking → không chạy trên thread của stream.
                        ? Mono.fromCallable(() -> saveTurn(id, request, done.response()))
                                .subscribeOn(Schedulers.boundedElastic())
                                .thenReturn(event)
                        : Mono.just(event))
                .onErrorResume(e -> Mono.just(new RagStreamEvent.Failed(e instanceof ApiException api
                        ? api.getBody() : GlobalExceptionHandler.internalError())));
    }

    private static ChatRequest chatRequest(ConversationDtos.AskRequest request) {
        return new ChatRequest(request.question(), request.topic(), request.topK());
    }

    private AskContext loadContext(UUID id) {
        return transactions.execute(status -> {
            Conversation conversation = require(id);
            List<ConversationMessage> all = conversation.getMessages();
            return new AskContext(List.copyOf(conversation.getDocumentIds()),
                    List.copyOf(all.subList(Math.max(0, all.size() - 12), all.size())));
        });
    }

    /** Lưu câu hỏi + câu trả lời đã chuẩn hóa (từ chối → câu "không tìm thấy" chuẩn, giống API). */
    private ConversationDtos.Response saveTurn(UUID id, ConversationDtos.AskRequest request, ChatResponse answer) {
        return transactions.execute(status -> {
            // Đọc lại kèm khóa dòng: sequence_no tính từ số tin nhắn hiện có, nên hai lượt hỏi đồng thời phải ghi lần lượt.
            Conversation conversation = conversations.findWithLockById(id)
                    .orElseThrow(() -> new ConversationNotFoundException(id));
            conversation.addMessage(new ConversationMessage("USER", request.question().strip(), null, List.of()));
            conversation.addMessage(new ConversationMessage("ASSISTANT", answer.answer(), answer.found(),
                    answer.sources()));
            return ConversationDtos.Response.from(conversations.save(conversation));
        });
    }

    private record AskContext(List<UUID> documentIds, List<ConversationMessage> history) {
    }

    private Conversation require(UUID id) {
        return conversations.findById(id).orElseThrow(() -> new ConversationNotFoundException(id));
    }

    private void validateScope(Set<UUID> ids) {
        if (ids.isEmpty()) return;
        List<SourceDocument> found = documents.findAllById(ids);
        if (found.size() != ids.size() || found.stream().anyMatch(d -> d.getStatus() != DocumentStatus.INDEXED)) {
            throw new InvalidDocumentScopeException();
        }
    }
}
