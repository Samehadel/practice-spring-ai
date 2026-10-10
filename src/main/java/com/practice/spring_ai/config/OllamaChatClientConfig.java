package com.practice.spring_ai.config;

import com.practice.spring_ai.advisors.CaseInsensitiveSafeGuardAdvisor;
import com.practice.spring_ai.advisors.TokenLoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OllamaChatClientConfig {

    @Bean
    public ChatClientBuilderCustomizer sharedChatClientDefaults() {
        return builder -> builder.defaultAdvisors(
                new SimpleLoggerAdvisor(),
                new TokenLoggingAdvisor());
    }

    @Bean
    public ChatClient.Builder ollamaChatClientBuilder(
            OllamaChatModel ollamaChatModel,
            ChatClientBuilderCustomizer sharedChatClientDefaults) {
        ChatClient.Builder builder = ChatClient.builder(ollamaChatModel);
        sharedChatClientDefaults.customize(builder);
        return builder;
    }

    @Bean
    public ChatClient ollamaChatClient(ChatClient.Builder ollamaChatClientBuilder) {
        ChatOptions defaultChatOptions = buildDefualtChatOptions();
        return ollamaChatClientBuilder.clone().defaultSystem("""
                You are a helpful assistant.
                Be concise and not waste time.
                """)
                .defaultAdvisors(new CaseInsensitiveSafeGuardAdvisor(List.of("Kill", "Harm", "Die")))
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
