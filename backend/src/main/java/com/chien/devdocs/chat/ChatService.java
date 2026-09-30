package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatRequest;
import org.springframework.stereotype.Service;

/**
 * Điểm vào duy nhất cho hỏi đáp: chọn cách thực hiện RAG theo {@link RagMode}.
 */
@Service
public class ChatService {

    private final RagService ragService;
    private final AdvisorRagService advisorRagService;

    public ChatService(RagService ragService, AdvisorRagService advisorRagService) {
        this.ragService = ragService;
        this.advisorRagService = advisorRagService;
    }

    public RagAnswer answer(ChatRequest request) {
        RagMode mode = request.modeOrDefault();
        return mode == RagMode.MANUAL
                ? ragService.answer(request)
                : advisorRagService.answer(request, mode);
    }
}
