package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatRequest;
import com.chien.devdocs.chat.dto.ChatResponse;
import com.chien.devdocs.common.exception.InvalidRequestException;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final RagService ragService;

    public ChatController(ChatService chatService, RagService ragService) {
        this.chatService = chatService;
        this.ragService = ragService;
    }

    /** Body: {"question": "...", "topic": "SPRING", "topK": 5, "mode": "MANUAL"} — chỉ question là bắt buộc. */
    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.answer(request).toResponse();
    }

    /**
     * Như {@link #chat}, nhưng trả lời dạng Server-Sent Events: {@code sources} → nhiều {@code token} → {@code done}
     * (hoặc {@code error}). Chỉ hỗ trợ mode MANUAL. Lỗi trước khi stream bắt đầu (400, 503) vẫn là HTTP status thường.
     */
    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@Valid @RequestBody ChatRequest request) {
        if (request.modeOrDefault() != RagMode.MANUAL) {
            throw new InvalidRequestException("Streaming chỉ hỗ trợ mode MANUAL. Dùng POST /api/v1/chat cho "
                    + request.mode() + ".");
        }
        return ragService.stream(request)
                .map(e -> ServerSentEvent.builder(e.data()).event(e.name()).build());
    }
}
