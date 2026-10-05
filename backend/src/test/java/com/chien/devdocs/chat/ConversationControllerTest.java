package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ConversationDtos;
import com.chien.devdocs.common.exception.ConversationNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConversationController.class)
class ConversationControllerTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ConversationService service;

    private static ConversationDtos.Response response(UUID id, String title, Set<UUID> scope) {
        return new ConversationDtos.Response(id, title, scope, List.of(), Instant.now(), Instant.now());
    }

    /** Frontend gọi khác origin: đổi tên dùng PATCH, đổi phạm vi dùng PUT → preflight phải được chấp nhận. */
    @ParameterizedTest
    @CsvSource({"PUT, /scope", "PATCH, ''", "POST, /messages", "DELETE, ''"})
    void preflightFromFrontendOriginAllowsEveryMethodTheFrontendUses(String method, String suffix) throws Exception {
        mvc.perform(options("/api/v1/conversations/{id}" + suffix, UUID.randomUUID())
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString(method)));
    }

    @Test
    void preflightFromUnknownOriginIsRejected() throws Exception {
        mvc.perform(options("/api/v1/conversations/{id}/scope", UUID.randomUUID())
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void renameReturnsUpdatedConversation() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.rename(id, "Ôn transaction")).thenReturn(response(id, "Ôn transaction", Set.of()));

        mvc.perform(patch("/api/v1/conversations/{id}", id).header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title": "Ôn transaction"}"""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                .andExpect(jsonPath("$.title").value("Ôn transaction"));
    }

    @Test
    void blankTitleIs400() throws Exception {
        mvc.perform(patch("/api/v1/conversations/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title": "   "}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scopeIsPassedToService() throws Exception {
        UUID id = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(service.setScope(id, Set.of(documentId))).thenReturn(response(id, "t", Set.of(documentId)));

        mvc.perform(put("/api/v1/conversations/{id}/scope", id).header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIds\": [\"" + documentId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentIds[0]").value(documentId.toString()));
    }

    @Test
    void blankOrTooLongQuestionIs400() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/conversations/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"question": "  "}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/conversations/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"" + "a".repeat(1500) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void askDelegatesToService() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.ask(eq(id), any())).thenReturn(response(id, "REQUIRED là gì?", Set.of()));

        mvc.perform(post("/api/v1/conversations/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"question": "REQUIRED là gì?", "topic": "SPRING"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("REQUIRED là gì?"));
    }

    @Test
    void unknownConversationIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new ConversationNotFoundException(id));

        mvc.perform(get("/api/v1/conversations/{id}", id)).andExpect(status().isNotFound());
    }
}
