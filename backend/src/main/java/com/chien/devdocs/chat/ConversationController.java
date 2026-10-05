package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ConversationDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private final ConversationService service;
    public ConversationController(ConversationService service) { this.service = service; }

    @GetMapping
    public List<ConversationDtos.Summary> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDtos.Response create(@RequestBody(required = false) ConversationDtos.CreateRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public ConversationDtos.Response get(@PathVariable UUID id) { return service.get(id); }

    @PatchMapping("/{id}")
    public ConversationDtos.Response rename(@PathVariable UUID id, @Valid @RequestBody ConversationDtos.RenameRequest request) {
        return service.rename(id, request.title());
    }

    @PutMapping("/{id}/scope")
    public ConversationDtos.Response scope(@PathVariable UUID id, @RequestBody ConversationDtos.ScopeRequest request) {
        return service.setScope(id, request.documentIds());
    }

    @PostMapping("/{id}/messages")
    public ConversationDtos.Response ask(@PathVariable UUID id, @Valid @RequestBody ConversationDtos.AskRequest request) {
        return service.ask(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
