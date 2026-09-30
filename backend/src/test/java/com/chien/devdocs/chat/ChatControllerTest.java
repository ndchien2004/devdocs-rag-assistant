package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatResponse;
import com.chien.devdocs.common.exception.AiServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.ConnectException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RagService ragService;

    private org.springframework.test.web.servlet.ResultActions chat(String json) throws Exception {
        return mvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void validQuestionReturnsAnswer() throws Exception {
        when(ragService.ask(any())).thenReturn(new ChatResponse("Trả lời [1]", true, List.of(), 42));

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
        when(ragService.ask(any())).thenThrow(new AiServiceUnavailableException(new ConnectException("refused")));

        chat("""
                {"question": "hi"}""")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("AI service unavailable"));
    }
}
