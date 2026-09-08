package com.practice.spring_ai.config;

import com.practice.spring_ai.advisors.TokenLoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OllamaRagChatClientConfig {

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
}
