package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.SourceDto;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {
    @Id
    private UUID id;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "conversation_document_scopes", joinColumns = @JoinColumn(name = "conversation_id"))
    @Column(name = "document_id", nullable = false)
    private Set<UUID> documentIds = new LinkedHashSet<>();

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<ConversationMessage> messages = new ArrayList<>();

    protected Conversation() {}

    public Conversation(Set<UUID> documentIds) {
        this.id = UUID.randomUUID();
        this.title = "Cuộc trò chuyện mới";
        this.documentIds = new LinkedHashSet<>(documentIds);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void rename(String title) { this.title = title.strip(); this.updatedAt = Instant.now(); }
    public void setDocumentIds(Set<UUID> ids) { this.documentIds = new LinkedHashSet<>(ids); this.updatedAt = Instant.now(); }
    public void addMessage(ConversationMessage message) {
        message.setSequenceNo(messages.size());
        messages.add(message);
        message.setConversation(this);
        if ("USER".equals(message.getRole()) && messages.stream().filter(m -> "USER".equals(m.getRole())).count() == 1
                && "Cuộc trò chuyện mới".equals(title)) {
            rename(message.getContent().length() > 60 ? message.getContent().substring(0, 57) + "…" : message.getContent());
        } else updatedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<UUID> getDocumentIds() { return documentIds; }
    public List<ConversationMessage> getMessages() { return messages; }
}
