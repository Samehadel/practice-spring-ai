package com.practice.spring_ai.config;

import com.practice.spring_ai.advisors.CaseInsensitiveSafeGuardAdvisor;
import com.practice.spring_ai.advisors.TokenLoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient openaiChatClient(OpenAiChatModel openAiChatModel) {
        ChatClient.Builder chatClientBuilder = ChatClient.builder(openAiChatModel);
        ChatOptions defaultChatOptions = ChatOptions.builder()
                .maxTokens(100)
                .temperature(0.8)
                .build();
        return chatClientBuilder
                .defaultAdvisors(buildDefaultAdvisors())
                .defaultSystem("""
                You are a helpful assistant.
                Be concise and not waste time.
                """)
                .defaultOptions(defaultChatOptions)
                .build();
    }

    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
        ChatClient.Builder chatClientBuilder = ChatClient.builder(ollamaChatModel);
        ChatOptions defaultChatOptions = ChatOptions.builder()
                .maxTokens(350)
                .temperature(0.8)
                .build();
        return chatClientBuilder.defaultSystem("""
                You are a helpful assistant.
                Be concise and not waste time.
                """)
                .defaultAdvisors(buildDefaultAdvisors())
                .defaultOptions(defaultChatOptions)
                .build();
    }

    private List<Advisor> buildDefaultAdvisors() {
        return List.of(
                new SimpleLoggerAdvisor(),
                new CaseInsensitiveSafeGuardAdvisor(List.of("Kill", "Harm", "Die")),
                new TokenLoggingAdvisor()
        );
    }
}
