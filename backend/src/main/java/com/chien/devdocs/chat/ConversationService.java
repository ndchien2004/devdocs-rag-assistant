package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatRequest;
import com.chien.devdocs.chat.dto.ConversationDtos;
import com.chien.devdocs.common.Topic;
import com.chien.devdocs.common.exception.ConversationNotFoundException;
import com.chien.devdocs.common.exception.InvalidDocumentScopeException;
import com.chien.devdocs.document.DocumentStatus;
import com.chien.devdocs.document.SourceDocument;
import com.chien.devdocs.document.SourceDocumentRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
        AskContext context = transactions.execute(status -> {
            Conversation conversation = require(id);
            List<ConversationMessage> all = conversation.getMessages();
            return new AskContext(List.copyOf(conversation.getDocumentIds()),
                    List.copyOf(all.subList(Math.max(0, all.size() - 12), all.size())));
        });

        // LLM lỗi → ném exception ở đây, chưa có gì được ghi.
        var answer = ragService.answer(new ChatRequest(request.question(), request.topic(), null),
                context.documentIds(), context.history());

        return transactions.execute(status -> {
            // Đọc lại kèm khóa dòng: sequence_no tính từ số tin nhắn hiện có, nên hai lượt hỏi đồng thời phải ghi lần lượt.
            Conversation conversation = conversations.findWithLockById(id)
                    .orElseThrow(() -> new ConversationNotFoundException(id));
            conversation.addMessage(new ConversationMessage("USER", request.question().strip(), null, List.of()));
            conversation.addMessage(new ConversationMessage("ASSISTANT", answer.answer(), answer.found(), answer.sources()));
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
