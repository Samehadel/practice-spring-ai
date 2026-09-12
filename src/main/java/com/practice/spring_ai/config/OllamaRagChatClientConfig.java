package com.practice.spring_ai.config;

import com.practice.spring_ai.advisors.TokenLoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OllamaRagChatClientConfig {


    @Bean
    public ChatClient ollamaRagChatClient(OllamaChatModel ollamaChatModel, ChatMemory chatMemory, VectorStore vectorStore) {
        Advisor chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .order(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER)
                .build();
        ChatOptions defaultChatOptions = buildDefualtChatOptions();
        List<Advisor> ragAdvisors = buildRagAdvisors(vectorStore);
        ragAdvisors.add(chatMemoryAdvisor);
        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(ragAdvisors)
                .defaultOptions(defaultChatOptions)
                .build();
    }

    private List<Advisor> buildRagAdvisors(VectorStore vectorStore) {
        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = buildRetrievalAugmentationAdvisor(vectorStore);
        return new ArrayList<>() {
            {
                add(new SimpleLoggerAdvisor(Integer.MAX_VALUE));
                add(new TokenLoggingAdvisor());
                add(retrievalAugmentationAdvisor);
            }
        };
    }

    private ChatOptions buildDefualtChatOptions() {
        return ChatOptions.builder()
                .maxTokens(350)
                .temperature(0.8)
                .build();
    }

    private RetrievalAugmentationAdvisor buildRetrievalAugmentationAdvisor(VectorStore vectorStore) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(vectorStore)
                        .topK(3).similarityThreshold(0.5).build())
                .build();
    }
}
