package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.SourceDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversation_messages")
public class ConversationMessage {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;
    @Column(nullable = false, length = 20)
    private String role;
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    private Boolean found;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<SourceDto> sources = List.of();
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConversationMessage() {}
    public ConversationMessage(String role, String content, Boolean found, List<SourceDto> sources) {
        this.id = UUID.randomUUID(); this.role = role; this.content = content; this.found = found;
        this.sources = sources == null ? List.of() : List.copyOf(sources); this.createdAt = Instant.now();
    }
    void setConversation(Conversation conversation) { this.conversation = conversation; }
    void setSequenceNo(int sequenceNo) { this.sequenceNo = sequenceNo; }
    public UUID getId() { return id; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public Boolean getFound() { return found; }
    public List<SourceDto> getSources() { return sources; }
    public Instant getCreatedAt() { return createdAt; }
}
