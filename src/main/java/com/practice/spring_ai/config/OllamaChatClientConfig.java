package com.practice.spring_ai.config;

import com.practice.spring_ai.advisors.CaseInsensitiveSafeGuardAdvisor;
import com.practice.spring_ai.advisors.TokenLoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OllamaChatClientConfig {

    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
        ChatClient.Builder chatClientBuilder = ChatClient.builder(ollamaChatModel);
        ChatOptions defaultChatOptions = buildDefualtChatOptions();
        return chatClientBuilder.defaultSystem("""
                You are a helpful assistant.
                Be concise and not waste time.
                """)
                .defaultAdvisors(buildDefaultAdvisors())
                .defaultOptions(defaultChatOptions)
                .build();
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