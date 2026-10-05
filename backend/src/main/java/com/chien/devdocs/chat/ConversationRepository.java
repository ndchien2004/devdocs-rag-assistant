package com.chien.devdocs.chat;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    List<Conversation> findAllByOrderByUpdatedAtDesc();

    /** Khóa dòng hội thoại tới hết transaction: hai lượt hỏi đồng thời ghi tin nhắn lần lượt, không trùng sequence_no. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Conversation> findWithLockById(UUID id);
}
