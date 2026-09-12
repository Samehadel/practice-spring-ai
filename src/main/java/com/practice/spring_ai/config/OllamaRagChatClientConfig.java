package com.practice.spring_ai.config;

import com.practice.spring_ai.advisors.TokenLoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OllamaRagChatClientConfig {

    @Value("classpath:/templates/prompts/systemPromptTemplate.st")
    private Resource systemPromptTemplate;

    @Bean
    public ChatClient ollamaRagChatClient(OllamaChatModel ollamaChatModel, ChatMemory chatMemory, VectorStore vectorStore) {
        Advisor chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .order(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER)
                .build();
        ChatOptions defaultChatOptions = buildDefualtChatOptions();
        List<Advisor> ragAdvisors = buildRagAdvisors(vectorStore);
        // ragAdvisors.add(chatMemoryAdvisor);
        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(ragAdvisors)
                .defaultOptions(defaultChatOptions)
                .build();
    }

    private List<Advisor> buildRagAdvisors(VectorStore vectorStore) {
        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = buildRetrievalAugmentationAdvisor(vectorStore);
        return new ArrayList<>() {
            {
                add(new SimpleLoggerAdvisor());
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
        VectorStoreDocumentRetriever vectorStoreDocumentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(3)
                .similarityThreshold(0.5)
                .build();

        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .promptTemplate(new PromptTemplate(systemPromptTemplate))
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(vectorStoreDocumentRetriever)
                .queryAugmenter(queryAugmenter)
                .build();
    }
}
