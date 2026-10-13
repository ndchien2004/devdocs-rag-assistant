package com.chien.devdocs.chat.dto;

import com.chien.devdocs.chat.Conversation;
import com.chien.devdocs.chat.ConversationMessage;
import com.chien.devdocs.common.Topic;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ConversationDtos {
    private ConversationDtos() {}
    public record CreateRequest(Set<UUID> documentIds) {}
    public record RenameRequest(@NotBlank @Size(max = 160) String title) {}
    public record ScopeRequest(Set<UUID> documentIds) {}
    /** @param topK bỏ trống = {@code app.rag.top-k} */
    public record AskRequest(@NotBlank @Size(max = 1000) String question, Topic topic, @Min(1) @Max(10) Integer topK) {
        public AskRequest(String question, Topic topic) { this(question, topic, null); }
    }
    public record Message(UUID id, String role, String content, Boolean found, List<SourceDto> sources, Instant createdAt) {
        public static Message from(ConversationMessage m) {
            return new Message(m.getId(), m.getRole(), m.getContent(), m.getFound(), m.getSources(), m.getCreatedAt());
        }
    }
    public record Response(UUID id, String title, Set<UUID> documentIds, List<Message> messages,
                           Instant createdAt, Instant updatedAt) {
        public static Response from(Conversation c) {
            return new Response(c.getId(), c.getTitle(), Set.copyOf(c.getDocumentIds()),
                    c.getMessages().stream().map(Message::from).toList(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }
    public record Summary(UUID id, String title, int messageCount, Instant updatedAt) {
        public static Summary from(Conversation c) { return new Summary(c.getId(), c.getTitle(), c.getMessages().size(), c.getUpdatedAt()); }
    }
}
