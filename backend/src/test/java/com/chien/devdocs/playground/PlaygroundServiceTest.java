package com.chien.devdocs.playground;

import com.chien.devdocs.support.FakeAiModels;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PlaygroundServiceTest {

    private final FakeAiModels.CountingChatModel chatModel = new FakeAiModels.CountingChatModel();
    private final PlaygroundService service = new PlaygroundService(
            ChatClient.builder(chatModel).build(), new FakeAiModels.BagOfWordsEmbeddingModel());

    @Test
    void helloSendsTheQuestionToTheChatModel() {
        chatModel.reset("Xin chào!");

        assertThat(service.hello("Chào bạn")).isEqualTo("Xin chào!");
        assertThat(chatModel.lastPrompt().getContents()).contains("Chào bạn");
    }

    @Test
    void embedReturnsDimensionsAndFirstFiveValues() {
        var res = service.embed("Spring Boot framework");

        assertThat(res.dimensions()).isEqualTo(1024);
        assertThat(res.head()).hasSize(5);
    }

    @Test
    void similarityOfIdenticalTextsIsOne() {
        assertThat(service.similarity("transaction rollback", "transaction rollback").cosineSimilarity())
                .isCloseTo(1.0, within(1e-6));
        assertThat(service.similarity("transaction rollback", "trời mưa").cosineSimilarity())
                .isCloseTo(0.0, within(1e-6));
    }
}
