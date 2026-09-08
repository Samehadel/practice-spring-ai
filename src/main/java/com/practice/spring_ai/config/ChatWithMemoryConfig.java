package com.practice.spring_ai.config;

import com.practice.spring_ai.advisors.CaseInsensitiveSafeGuardAdvisor;
import com.practice.spring_ai.advisors.TokenLoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class ChatWithMemoryConfig {
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
    public ChatClient ollamaInMemoryChatClient(OllamaChatModel ollamaChatModel, ChatMemory chatMemory) {
        Advisor chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        ChatOptions defaultChatOptions = buildDefualtChatOptions();
        List<Advisor> allAdvisors = buildDefaultAdvisors();
        allAdvisors.add(chatMemoryAdvisor);
        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(allAdvisors)
                .defaultOptions(defaultChatOptions)
                .build();
    }

    @Bean
    public ChatClient ollamaRagChatClient(OllamaChatModel ollamaChatModel, ChatMemory chatMemory) {
        Advisor chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        ChatOptions defaultChatOptions = buildDefualtChatOptions();
        List<Advisor> ragAdvisors = buildRagAdvisors();
        ragAdvisors.add(chatMemoryAdvisor);
        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(ragAdvisors)
                .defaultOptions(defaultChatOptions)
                .build();
    }

    private List<Advisor> buildRagAdvisors() {
        return new ArrayList<>() {
            {
                add(new SimpleLoggerAdvisor());
                add(new TokenLoggingAdvisor());
            }
        };
    }

    private ChatOptions buildDefualtChatOptions() {
        return ChatOptions.builder()
                .maxTokens(350)
                .temperature(0.8)
                .build();
    }

    private List<Advisor> buildDefaultAdvisors() {
        return new ArrayList<>() {
            {
                add(new SimpleLoggerAdvisor());
                add(new CaseInsensitiveSafeGuardAdvisor(List.of("Kill", "Harm", "Die")));
                add(new TokenLoggingAdvisor());
            }
        };
    }

}
