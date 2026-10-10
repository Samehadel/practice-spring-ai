package com.practice.spring_ai.config;

import com.practice.spring_ai.advisors.CaseInsensitiveSafeGuardAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OllamaChatWithMemoryClientConfig {
    @Value("${spring.ai.chat.memory.max.messages}")
    private int maxMessages;

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository jdbcChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    public ChatClient ollamaInMemoryChatClient(ChatClient.Builder ollamaChatClientBuilder, ChatMemory chatMemory) {
        ChatOptions defaultChatOptions = buildDefualtChatOptions();
        return ollamaChatClientBuilder.clone()
                .defaultAdvisors(new CaseInsensitiveSafeGuardAdvisor(List.of("Kill", "Harm", "Die")))
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultOptions(defaultChatOptions.mutate())
                .build();
    }

    private ChatOptions buildDefualtChatOptions() {
        return ChatOptions.builder()
                .maxTokens(350)
                .temperature(0.8)
                .build();
    }
}
