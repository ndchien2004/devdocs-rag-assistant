package com.chien.devdocs.chat;

import com.chien.devdocs.common.exception.AiServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.chien.devdocs.chat.dto.ChatResponse;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.net.ConnectException;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ChatService chatService;

    @MockitoBean
    RagService ragService;

    private org.springframework.test.web.servlet.ResultActions chat(String json) throws Exception {
        return mvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void validQuestionReturnsAnswer() throws Exception {
        when(chatService.answer(any())).thenReturn(new RagAnswer("Trả lời [1]", true, List.of(), 42, true, 1));

        chat("""
                {"question": "REQUIRED là gì?", "topic": "SPRING", "topK": 5}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Trả lời [1]"))
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.latencyMs").value(42));
    }

    @Test
    void blankQuestionIs400() throws Exception {                 // TC-QRY-06
        chat("""
                {"question": "   "}""").andExpect(status().isBadRequest());
        chat("{}").andExpect(status().isBadRequest());
    }

    @Test
    void questionLongerThan1000CharsIs400() throws Exception {   // TC-QRY-07
        chat("{\"question\": \"" + "a".repeat(1500) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void topKOutOfRangeIs400() throws Exception {
        chat("""
                {"question": "hi", "topK": 11}""").andExpect(status().isBadRequest());
        chat("""
                {"question": "hi", "topK": 0}""").andExpect(status().isBadRequest());
    }

    @Test
    void unknownTopicIs400() throws Exception {
        chat("""
                {"question": "hi", "topic": "PYTHON"}""").andExpect(status().isBadRequest());
    }

    @Test
    void ollamaDownIs503() throws Exception {                    // TC-QRY-09
        when(chatService.answer(any())).thenThrow(new AiServiceUnavailableException(new ConnectException("refused")));

        chat("""
                {"question": "hi"}""")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("AI service unavailable"));
    }

    // ------------------------------------------------------------------ POST /chat/stream

    private org.springframework.test.web.servlet.ResultActions stream(String json) throws Exception {
        return mvc.perform(post("/api/v1/chat/stream").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM).content(json));
    }

    @Test
    void streamSendsServerSentEventsWithJsonData() throws Exception {
        when(ragService.stream(any())).thenReturn(Flux.just(
                new RagStreamEvent.Sources(List.of()),
                new RagStreamEvent.Token(" REQUIRED\n"),
                new RagStreamEvent.Done(new ChatResponse(" REQUIRED", true, List.of(), 7))));

        MvcResult result = stream("""
                {"question": "REQUIRED là gì?"}""")
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:sources\ndata:[]")))
                // Token bọc JSON: dấu cách đầu và \n được giữ nguyên, không bị SSE cắt dòng.
                .andExpect(content().string(containsString("event:token\ndata:{\"text\":\" REQUIRED\\n\"}")))
                .andExpect(content().string(containsString("event:done\ndata:{\"answer\":\" REQUIRED\",\"found\":true")));
    }

    @Test
    void streamValidatesTheRequestLikeChat() throws Exception {      // TC-QRY-06 / 07
        stream("""
                {"question": "   "}""").andExpect(status().isBadRequest());
        stream("{\"question\": \"" + "a".repeat(1500) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void streamOnlySupportsManualMode() throws Exception {
        stream("""
                {"question": "hi", "mode": "QA_ADVISOR"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        verifyNoInteractions(ragService);
    }

    @Test
    void streamFailingBeforeItStartsIsA503Status() throws Exception {   // TC-QRY-09
        when(ragService.stream(any())).thenThrow(new AiServiceUnavailableException(new ConnectException("refused")));

        stream("""
                {"question": "hi"}""")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("AI service unavailable"));
    }
}
