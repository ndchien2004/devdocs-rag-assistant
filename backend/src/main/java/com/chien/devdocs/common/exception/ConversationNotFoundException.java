package com.chien.devdocs.common.exception;

import org.springframework.http.HttpStatus;
import java.util.UUID;

public class ConversationNotFoundException extends ApiException {
    public ConversationNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Conversation not found", "Không tìm thấy cuộc trò chuyện.");
    }
}
