package com.chien.devdocs.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    /**
     * ChatClient dùng chung. ChatClient.Builder được Spring AI auto-configure từ ChatModel (Ollama),
     * nên đổi sang model/provider khác chỉ cần sửa cấu hình, không sửa code nghiệp vụ (NFR-05).
     */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
